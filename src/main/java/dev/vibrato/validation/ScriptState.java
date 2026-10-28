package dev.vibrato.validation;

import java.util.List;
import java.util.Map;

/** Tick-driven scheduler. No clock advances until the world is ready. */
public final class ScriptState {
    public record Action(ScriptPlan.Step step, boolean timedOut) {}
    private final List<ScriptPlan.Step> steps;
    private int index;
    private double elapsed;
    private double waitElapsed;
    private boolean waiting;
    public ScriptState(List<ScriptPlan.Step> steps) { this.steps = List.copyOf(steps); }
    public boolean finished() { return index == steps.size(); }
    /** Proof comes from the actual pending final EXIT, completed assignment and successful idle barrier. */
    public Map<String, Object> settleEvidence(String assignment, double budget,
            List<Map<String, Object>> commands, List<Map<String, Object>> outcomes, boolean idle) {
        if (!idle || assignment == null || !assignment.startsWith("@") || !Double.isFinite(budget)
                || budget <= 0 || finished() || index != steps.size() - 1 || commands.isEmpty()) return Map.of();
        ScriptPlan.Step pending = steps.get(index);
        double remaining = pending.delaySeconds() - elapsed;
        var barrier = commands.getLast();
        if (pending.kind() != ScriptPlan.Kind.EXIT || pending.delaySeconds() != budget || remaining <= 0
                || !String.valueOf(barrier.get("action")).startsWith("wait-idle ")
                || !"idle".equals(barrier.get("status"))) return Map.of();
        Map<String, Object> command = null;
        for (var candidate : commands) if (assignment.equals(candidate.get("action"))) command = candidate;
        if (command == null || !"completed".equals(command.get("status"))
                || !(command.get("seconds") instanceof Number dispatched)) return Map.of();
        boolean completed = false;
        for (var outcome : outcomes) {
            if (!(outcome.get("seconds") instanceof Number seconds) || seconds.doubleValue() < dispatched.doubleValue()) continue;
            if (!"completed".equals(outcome.get("status"))) return Map.of();
            completed = true;
        }
        if (!completed) return Map.of();
        return Map.of("scriptPhase", "post_task_settle", "assignedTask", assignment,
                "assignedTaskStatus", "completed", "settleRemainingSeconds", remaining,
                "settleBudgetSeconds", budget, "settleSource", "completed_command_idle_barrier_pending_exit");
    }
    public Action tick(double seconds, boolean ready, boolean idle) {
        if (!ready || finished()) return null;
        if (seconds < 0 || !Double.isFinite(seconds)) throw new IllegalArgumentException("invalid elapsed time");
        ScriptPlan.Step step = steps.get(index);
        if (!waiting) {
            elapsed += seconds;
            if (elapsed < step.delaySeconds()) return null;
            if (step.kind() != ScriptPlan.Kind.WAIT_IDLE) return advance(step, false);
            waiting = true;
            // Preserve overshoot so timeout and delays use elapsed time rather than tick count.
            waitElapsed = elapsed - step.delaySeconds();
        } else waitElapsed += seconds;
        if (idle) return advance(step, false);
        if (waitElapsed >= step.timeoutSeconds()) return advance(step, true);
        return null;
    }
    private Action advance(ScriptPlan.Step step, boolean timeout) {
        index++; elapsed = 0; waitElapsed = 0; waiting = false;
        return new Action(step, timeout);
    }
}
