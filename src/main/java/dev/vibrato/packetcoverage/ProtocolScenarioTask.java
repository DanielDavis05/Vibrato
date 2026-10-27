package dev.vibrato.packetcoverage;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;
import dev.vibrato.protocol.scenarios.ClientProtocolScenarioTask;
import dev.vibrato.tasksystem.Task;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import java.io.IOException;
import java.util.List;

/** Explicit local fixture operation. Dispatch and observed packet success are separate conditions. */
public final class ProtocolScenarioTask extends Task {
    private final String action;
    private final boolean server;
    private final String scenario;
    private final ClientProtocolScenarioTask child;
    private long ticks;
    private boolean finished;
    private boolean dispatched;
    private long[] before;

    public ProtocolScenarioTask(String action, boolean server) {
        if (!action.matches("[a-z0-9_]{1,60}")) throw new IllegalArgumentException("Invalid protocol scenario");
        if (!server && !ClientProtocolScenarioTask.supportedActions().contains(action)) throw new IllegalArgumentException("Unknown client scenario: " + action);
        this.action = action;
        this.server = server;
        this.scenario = (server ? "fixture_" : "client_") + action;
        this.child = server ? null : new ClientProtocolScenarioTask(action);
    }

    @Override protected void onStart(Vibrato mod) {
        if (!PacketCoverage.enabled()) throw new IllegalStateException("Launch with -Dvibrato.packetCoverage=true");
        ProtocolCoverageController.requireLocalFixture();
        ticks = 0; finished = false; dispatched = false;
        PacketCoverage.beginScenario(scenario);
        PacketCoverage.setTask(getClass().getSimpleName(), "none");
        before = expected().stream().mapToLong(heading -> count(heading)).toArray();
    }

    @Override protected Task onTick(Vibrato mod) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) return null;
        ticks++;
        if (client.currentScreen instanceof DeathScreen) client.player.requestRespawn();
        if (ticks == 1) {
            client.setScreen(null);
            // Native sneak input dismounts before a new fixture action, so prior boat steering
            // cannot change the packet variants produced by later walking scenarios.
            if (client.player.hasVehicle()) client.options.sneakKey.setPressed(true);
            if (server) {
                client.getNetworkHandler().sendChatCommand("vprotocol run " + action);
                dispatched = true;
            } else {
                var mode = ClientProtocolScenarioTask.fixtureGameMode(action);
                if (mode != null) client.getNetworkHandler().sendChatCommand("gamemode " + mode.getName());
                String setup = ClientProtocolScenarioTask.fixtureSetup(action);
                if (!setup.equals("none")) client.getNetworkHandler().sendChatCommand("vprotocol run " + setup);
            }
        }
        if (ticks == 10) client.options.sneakKey.setPressed(false);
        if (!server && ticks >= 20 && !dispatched && child.isFinished(mod)) dispatched = true;
        boolean observed = true;
        List<String> expected = expected();
        for (int i = 0; i < expected.size(); i++) if (count(expected.get(i)) <= before[i]) observed = false;
        if (server && ticks >= 40 || !server && dispatched && observed && ticks >= 22) {
            finished = true;
            finishReport(mod, observed);
            return null;
        }
        if (ticks >= 600) {
            finished = true;
            finishReport(mod, false);
            return null;
        }
        return server || ticks < 20 || dispatched ? null : child;
    }

    private List<String> expected() { return server ? List.of() : ClientProtocolScenarioTask.expectedPackets(action); }
    private long count(String heading) {
        try {
            return ProtocolReports.current().packets().stream().filter(row -> row.packet().name().equals(heading)
                            && row.packet().direction().equals("serverbound"))
                    .flatMap(row -> row.attributions().stream()).filter(a -> a.scenario().equals(scenario)).mapToLong(PacketCoverageReport.Attribution::count).sum();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    private void finishReport(Vibrato mod, boolean observed) {
        try {
            ProtocolReports.write();
            mod.log("Protocol " + scenario + ": " + (server ? "fixture command sent; inspect coverage report" : observed ? "expected packets observed" : "incomplete; expected packets were not observed"));
        } catch (IOException e) { Debug.logWarning("Protocol report failed: " + e.getMessage()); }
    }
    @Override protected void onStop(Vibrato mod, Task interruptTask) {
        MinecraftClient.getInstance().options.sneakKey.setPressed(false);
        PacketCoverage.endScenario();
    }
    @Override public boolean isFinished(Vibrato mod) { return finished; }
    @Override protected boolean isEqual(Task other) { return other instanceof ProtocolScenarioTask task && scenario.equals(task.scenario); }
    @Override protected String toDebugString() { return "Protocol " + scenario; }
}
