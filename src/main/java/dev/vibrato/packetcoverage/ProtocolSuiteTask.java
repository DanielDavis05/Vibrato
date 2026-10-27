package dev.vibrato.packetcoverage;

import dev.vibrato.Vibrato;
import dev.vibrato.protocol.scenarios.ClientProtocolScenarioTask;
import dev.vibrato.tasksystem.Task;
import java.util.ArrayList;
import java.util.List;

/** Fixture-only sequence. Its completion is not a claim that the complete protocol was observed. */
public final class ProtocolSuiteTask extends Task {
    private record Step(String action, boolean server) {}
    private final List<Step> steps = new ArrayList<>();
    private int index;
    private ProtocolScenarioTask current;

    public ProtocolSuiteTask() {
        for (String group : List.of("ui", "scoreboard", "world", "entities", "blocks", "inventory", "client_state", "registry_state", "entity_motion", "container", "unknown_deleted_signature", "unknown_player_removed")) steps.add(new Step(group, true));
        ClientProtocolScenarioTask.supportedActions().stream().sorted().forEach(action -> steps.add(new Step(action, false)));
        // Common cookie requests intentionally take the native unexpected-response disconnect path.
        steps.add(new Step("common", true));
    }
    @Override protected void onStart(Vibrato mod) { index = 0; current = null; }
    @Override protected Task onTick(Vibrato mod) {
        if (current != null && current.isFinished(mod)) { current.stop(mod); current = null; index++; }
        if (index >= steps.size()) return null;
        if (current == null) {
            Step step = steps.get(index);
            current = new ProtocolScenarioTask(step.action(), step.server());
        }
        setDebugState("Scenario " + (index + 1) + "/" + steps.size());
        return current;
    }
    @Override protected void onStop(Vibrato mod, Task interrupted) { PacketCoverage.endScenario(); }
    @Override public boolean isFinished(Vibrato mod) { return index >= steps.size(); }
    @Override protected boolean isEqual(Task other) { return other instanceof ProtocolSuiteTask; }
    @Override protected String toDebugString() { return "Protocol fixture suite"; }
}
