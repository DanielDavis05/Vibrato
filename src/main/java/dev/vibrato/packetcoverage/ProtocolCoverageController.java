package dev.vibrato.packetcoverage;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;
import dev.vibrato.director.DirectorTask;
import dev.vibrato.eventbus.EventBus;
import dev.vibrato.eventbus.events.ClientTickEvent;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.tasksystem.TaskChain;
import dev.vibrato.tasksystem.TaskRunner;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.network.ServerAddress;
import java.io.IOException;

/** Optional test driver. No protocol scenario is enrolled in ordinary autonomous play. */
public final class ProtocolCoverageController {
    private static ProtocolConnectTask connection;
    private static TaskChain connectionChain;
    private static boolean started;
    private static boolean suiteStarted;
    private static long joinedTicks;
    private ProtocolCoverageController() {}

    public static void install(Vibrato mod) {
        if (!PacketCoverage.enabled()) return;
        EventBus.subscribe(ClientTickEvent.class, event -> tick(mod));
    }
    private static void tick(Vibrato mod) {
        var client = MinecraftClient.getInstance();
        String auto = System.getProperty("vibrato.protocolConnect");
        if (!started && auto != null && client.currentScreen instanceof TitleScreen) {
            started = true;
            connection = new ProtocolConnectTask(auto, System.getProperty("vibrato.packetCoverageScenario", "protocol_connect"));
            connectionChain = new TaskChain(new TaskRunner(mod)) {
                @Override protected void onTick(Vibrato context) { connection.tick(context, this); }
                @Override protected void onStop(Vibrato context) { connection.stop(context); }
                @Override public void onInterrupt(Vibrato context, TaskChain other) { connection.stop(context); }
                @Override public float getPriority(Vibrato context) { return 0; }
                @Override public boolean isActive() { return !connection.isFinished(mod); }
                @Override public String getName() { return "Protocol lifecycle"; }
            };
        }
        if (connection != null && !connection.isFinished(mod)) {
            connectionChain.tick(mod);
            return;
        }
        if (connection != null) {
            connection.stop(mod); connection = null;
            write();
            if (!Vibrato.inGame() && Boolean.getBoolean("vibrato.protocolAutoExit")) client.scheduleStop();
        }
        if (!Vibrato.inGame()) {
            // A fixture may intentionally disconnect after a Play packet; preserve that task's
            // attribution until the disconnect has been handled and the capture has been saved.
            if (suiteStarted && client.currentScreen instanceof DisconnectedScreen) {
                write();
                if (Boolean.getBoolean("vibrato.protocolAutoExit")) client.scheduleStop();
            }
            return;
        }
        joinedTicks++;
        if (started && !suiteStarted && joinedTicks >= 40 && Boolean.getBoolean("vibrato.protocolSuite")) {
            suiteStarted = true;
            mod.runUserTask(new ProtocolSuiteTask(), () -> {
                write();
                if (Boolean.getBoolean("vibrato.protocolAutoExit")) client.scheduleStop();
            });
        } else if (started && !Boolean.getBoolean("vibrato.protocolSuite") && joinedTicks >= 100 && Boolean.getBoolean("vibrato.protocolAutoExit")) {
            write(); client.scheduleStop();
        }
        var chain = mod.getTaskRunner().getCurrentTaskChain();
        var tasks = chain == null ? java.util.List.<Task>of() : chain.getTasks();
        if (tasks.isEmpty() || !tasks.getFirst().isActive()) {
            PacketCoverage.setTask("none", "none");
        } else {
            Task root = tasks.getFirst();
            String step = root instanceof DirectorTask director ? director.currentStepDescription().split("\\{", 2)[0] : "none";
            PacketCoverage.setTask(root.getClass().getSimpleName(), step);
        }
    }
    public static void requireLoopback(String address) {
        String host = ServerAddress.parse(address).getAddress();
        if (!host.equals("127.0.0.1") && !host.equals("localhost") && !host.equals("::1")) throw new IllegalArgumentException("Protocol fixtures must use a loopback server");
    }
    public static void requireLocalFixture() {
        var client = MinecraftClient.getInstance();
        if (client.isInSingleplayer()) return;
        var server = client.getCurrentServerEntry();
        if (server == null) throw new IllegalStateException("Join an isolated local fixture before running protocol actions");
        requireLoopback(server.address);
    }
    private static void write() {
        try { ProtocolReports.write(); }
        catch (IOException e) { Debug.logWarning("Protocol report failed: " + e.getMessage()); }
    }
}
