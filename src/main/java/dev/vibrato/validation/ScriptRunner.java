package dev.vibrato.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.eventbus.EventBus;
import dev.vibrato.eventbus.events.ClientTickEvent;
import dev.vibrato.eventbus.events.TaskFinishedEvent;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Opt-in development harness. All mutable state is confined to the client thread. */
public final class ScriptRunner {
    private final Vibrato mod;
    private final ScriptState state;
    private final WorldReadyGate readyGate = new WorldReadyGate();
    private final Path result;
    private final List<Map<String, Object>> commands = new ArrayList<>();
    private final List<Map<String, Object>> taskOutcomes = new ArrayList<>();
    private final List<Map<String, Object>> timeline = new ArrayList<>();
    private long startNanos, lastTimelineNanos;
    private long previousNanos;
    private long heartbeatNanos;
    private int heartbeatTicks;
    private boolean forward, sprint, haveSample;
    private double x, z, movingDistance;
    private long movingTicks, samples, pathStationaryTicks, forwardReleases, sprintRestarts;
    private boolean written;

    private ScriptRunner(Vibrato mod, List<ScriptPlan.Step> steps, Path result) {
        this.mod = mod;
        this.state = new ScriptState(steps);
        this.result = result;
    }

    public static void install(Vibrato mod) {
        String file = System.getProperty("vibrato.script");
        if (file == null || file.isBlank()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        Path result = client.runDirectory.toPath().resolve("script-result.json");
        try {
            ScriptRunner runner = new ScriptRunner(mod, ScriptPlan.parse(Files.readAllLines(Path.of(file))), result);
            EventBus.subscribe(ClientTickEvent.class, event -> runner.tick());
            EventBus.subscribe(TaskFinishedEvent.class, runner::taskFinished);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> runner.finish("shutdown"), "vibrato-script-result"));
            Debug.logMessage("Script loaded: " + file);
        } catch (IOException | IllegalArgumentException e) {
            Debug.logError("Cannot load script: " + e.getMessage());
            try { writeAtomic(result, Map.of("status", "load_error", "error", e.toString())); }
            catch (IOException failure) { Debug.logError("Cannot write script result: " + failure); }
            client.stop();
        }
    }

    private void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.nanoTime();
        if (startNanos == 0) startNanos = now;
        double dt = previousNanos == 0 ? 0 : Math.max(0, (now - previousNanos) / 1e9);
        previousNanos = now;
        if (client.player != null && client.world != null) {
            if (Boolean.getBoolean("vibrato.campaignTelemetry")) heartbeatTicks++;
            if (heartbeatNanos == 0) heartbeatNanos = now;
            if (Boolean.getBoolean("vibrato.campaignTelemetry") && now - heartbeatNanos >= 1_000_000_000L) {
                var activeChain = mod.getTaskRunner().getCurrentTaskChain();
                var activeTasks = activeChain == null ? List.<dev.vibrato.tasksystem.Task>of() : activeChain.getTasks();
                var currentLeaf = activeTasks.isEmpty() ? null : activeTasks.getLast();
                Map<String, Object> heartbeat = new LinkedHashMap<>();
                heartbeat.put("seconds", elapsed());
                heartbeat.put("position", List.of(client.player.getX(), client.player.getY(), client.player.getZ()));
                heartbeat.put("health", client.player.getHealth());
                heartbeat.put("dead", client.player.isDead());
                heartbeat.put("dimension", client.world.getRegistryKey().getValue().toString());
                heartbeat.put("leaf", currentLeaf == null ? null : currentLeaf.toString());
                heartbeat.put("tps", heartbeatTicks * 1e9 / (now - heartbeatNanos));
                try { writeAtomic(client.runDirectory.toPath().resolve("campaign-heartbeat.json"), heartbeat); }
                catch (IOException error) { Debug.logInternal("Campaign heartbeat write failed: " + error.getMessage()); }
                heartbeatNanos = now;
                heartbeatTicks = 0;
            }
        }
        boolean ready = readyGate.ready(client.player != null && client.world != null, client.currentScreen != null);
        if (!ready) { haveSample = false; return; }
        boolean path = mod.getClientBaritone().getPathingBehavior().isPathing()
                || mod.getClientBaritone().getPathingBehavior().getInProgress().isPresent();
        sample(client, path);
        if (lastTimelineNanos == 0 || now - lastTimelineNanos >= 1_000_000_000L) {
            lastTimelineNanos = now;
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("seconds", elapsed());
            point.put("position", List.of(client.player.getX(), client.player.getY(), client.player.getZ()));
            point.put("dimension", client.world.getRegistryKey().getValue().toString());
            point.put("health", client.player.getHealth());
            point.put("pathing", path);
            var chain = mod.getTaskRunner().getCurrentTaskChain();
            var tasks = chain == null ? List.<dev.vibrato.tasksystem.Task>of() : chain.getTasks();
            var leaf = tasks.isEmpty() ? null : tasks.getLast();
            point.put("leafClass", leaf == null ? null : leaf.getClass().getSimpleName());
            point.put("leaf", leaf == null ? null : leaf.toString());
            timeline.add(point);
        }
        boolean idle = !path && !mod.getUserTaskChain().isActive();
        ScriptState.Action action = state.tick(dt, true, idle);
        if (action == null) return;
        ScriptPlan.Step step = action.step();
        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("action", step.text());
        outcome.put("status", action.timedOut() ? "timeout" : "dispatched");
        commands.add(outcome);
        try {
            switch (step.kind()) {
                case CLIENT -> {
                    String prefix = Vibrato.getCommandExecutor().getCommandPrefix();
                    if (prefix.isEmpty()) throw new IllegalStateException("client command prefix is empty");
                    Vibrato.getCommandExecutor().execute(prefix + step.text().substring(1),
                            () -> outcome.put("status", "completed"), error -> {
                                outcome.put("status", "error"); outcome.put("error", error.getMessage());
                            });
                }
                case VANILLA -> {
                    client.player.networkHandler.sendChatCommand(step.text().substring(1));
                    outcome.put("status", "sent");
                }
                case WAIT_IDLE -> { if (!action.timedOut()) outcome.put("status", "idle"); }
                case EXIT -> { outcome.put("status", "exit"); finish("exit"); client.stop(); }
            }
        } catch (RuntimeException e) {
            outcome.put("status", "error"); outcome.put("error", e.toString());
            Debug.logError("Script action failed: " + e);
        }
        if (state.finished() && step.kind() != ScriptPlan.Kind.EXIT) finish("finished");
    }

    private double elapsed() { return startNanos == 0 ? 0 : (System.nanoTime() - startNanos) / 1e9; }

    private void taskFinished(TaskFinishedEvent event) {
        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("seconds", elapsed());
        outcome.put("task", event.lastTaskRan == null ? null : event.lastTaskRan.toString());
        outcome.put("durationSeconds", event.durationSeconds);
        String failure = event.lastTaskRan == null ? null : event.lastTaskRan.failureReason();
        outcome.put("status", failure != null ? "failed" : event.cancelled ? "cancelled" : "completed");
        outcome.put("failureReason", failure);
        taskOutcomes.add(outcome);
    }

    private void sample(MinecraftClient client, boolean path) {
        var player = client.player;
        boolean nextForward = player.input.movementForward > 0;
        boolean nextSprint = player.isSprinting();
        if (haveSample) {
            if (forward && !nextForward) forwardReleases++;
            if (!sprint && nextSprint) sprintRestarts++;
            double distance = Math.hypot(player.getX() - x, player.getZ() - z);
            if (distance > 0.005) { movingDistance += distance; movingTicks++; }
            else if (path) pathStationaryTicks++;
            samples++;
        }
        x = player.getX(); z = player.getZ(); forward = nextForward; sprint = nextSprint; haveSample = true;
    }

    private synchronized void finish(String status) {
        if (written) return;
        written = true;
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("sampleTicks", samples);
        metrics.put("movingTicks", movingTicks);
        metrics.put("movingDistanceBlocks", movingDistance);
        metrics.put("movingSpeedBlocksPerSecond", movingTicks == 0 ? 0 : movingDistance * 20 / movingTicks);
        metrics.put("forwardReleases", forwardReleases);
        metrics.put("sprintRestarts", sprintRestarts);
        metrics.put("pathStationaryTicks", pathStationaryTicks);
        metrics.put("stuckFirings", null);
        metrics.put("taskRestarts", null);
        try { writeAtomic(result, Map.of("status", status, "commands", commands, "metrics", metrics,
                "taskOutcomes", taskOutcomes, "timeline", timeline)); }
        catch (IOException e) { Debug.logError("Cannot write script result: " + e); }
    }

    private static void writeAtomic(Path result, Object data) throws IOException {
        Path temp = Files.createTempFile(result.getParent(), "script-result-", ".tmp");
        try {
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), data);
            try { Files.move(temp, result, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, result, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
