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
        String selection = System.getProperty("vibrato.protocolSteps", "");
        if (!selection.isBlank()) {
            for (String value : selection.split(",")) {
                String[] parts = value.split(":", 2);
                if (parts.length != 2 || !(parts[0].equals("fixture") || parts[0].equals("client")))
                    throw new IllegalArgumentException("Protocol steps use fixture:name or client:name");
                steps.add(new Step(parts[1], parts[0].equals("fixture")));
            }
            return;
        }
        for (String group : List.of("ui", "scoreboard", "world", "entities", "blocks", "inventory", "client_state", "registry_state", "entity_motion", "container", "unknown_deleted_signature", "unknown_player_removed", "block_effects", "chunk_refresh", "sign_editor", "written_book", "horse_screen", "merchant_screen", "play_sync", "ghost_recipe", "projectile_spawn", "projectile_update", "chunk_unload", "watercraft", "vehicle_mount", "vehicle_correction", "death", "resource_pack_unavailable", "unsigned_player_chat", "keepalive")) steps.add(new Step(group, true));
        ClientProtocolScenarioTask.supportedActions().stream()
                .filter(action -> !action.equals("player_session_untrusted"))
                .sorted().forEach(action -> steps.add(new Step(action, false)));
        steps.add(new Step("reconfigure", true));
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
