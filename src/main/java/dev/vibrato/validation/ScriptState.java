package dev.vibrato.validation;

import java.util.List;

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
