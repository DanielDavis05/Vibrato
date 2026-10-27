package dev.vibrato.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.eventbus.EventBus;
import dev.vibrato.eventbus.events.ClientTickEvent;
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
    private final Path result;
    private final List<Map<String, Object>> commands = new ArrayList<>();
    private long previousNanos;
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
        double dt = previousNanos == 0 ? 0 : Math.max(0, (now - previousNanos) / 1e9);
        previousNanos = now;
        boolean ready = client.player != null && client.world != null && client.currentScreen == null;
        if (!ready) { haveSample = false; return; }
        boolean path = mod.getClientBaritone().getPathingBehavior().isPathing()
                || mod.getClientBaritone().getPathingBehavior().getInProgress().isPresent();
        sample(client, path);
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
        try { writeAtomic(result, Map.of("status", status, "commands", commands, "metrics", metrics)); }
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
