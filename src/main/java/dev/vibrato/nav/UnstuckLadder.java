package dev.vibrato.nav;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import static dev.vibrato.nav.UnstuckAction.*;

/**
 * Escalating recovery for a stuck state. Each rung is handed out once per episode, and the last rung repeats once the
 * ladder is exhausted. An episode ends with {@link #onProgress()}.
 */
public final class UnstuckLadder {

    public static final long COOLDOWN_TICKS = 10;

    private static final Map<StuckState, UnstuckAction[]> RUNGS = new EnumMap<>(StuckState.class);

    static {
        RUNGS.put(StuckState.EDGE_JAM, new UnstuckAction[]{
                RECENTER_ON_BLOCK, JUMP, SIDESTEP_LEFT, SIDESTEP_RIGHT, BACK_OFF_AND_REPATH, BREAK_BLOCKING_BLOCK, ABANDON_TASK});
        RUNGS.put(StuckState.STATIONARY_WITH_INTENT, new UnstuckAction[]{
                JUMP, SIDESTEP_LEFT, SIDESTEP_RIGHT, BACK_OFF_AND_REPATH, BREAK_BLOCKING_BLOCK, PLACE_STEP_BLOCK, DIG_UP, DIG_DOWN, ABANDON_TASK});
        RUNGS.put(StuckState.OSCILLATING, new UnstuckAction[]{
                BACK_OFF_AND_REPATH, SIDESTEP_LEFT, SIDESTEP_RIGHT, PLACE_STEP_BLOCK, ABANDON_TASK});
        RUNGS.put(StuckState.SPINNING, new UnstuckAction[]{
                BACK_OFF_AND_REPATH, JUMP, ABANDON_TASK});
        RUNGS.put(StuckState.WATER_DRIFT, new UnstuckAction[]{
                JUMP, WATER_FORWARD_SPRINT, SIDESTEP_LEFT, BACK_OFF_AND_REPATH, ABANDON_TASK});
        RUNGS.put(StuckState.LOW_AIR, new UnstuckAction[]{
                SWIM_TO_SURFACE});
    }

    private final int[] _nextRung = new int[StuckState.values().length];
    private boolean _fired;
    private long _lastFiredTick;

    /**
     * The next unused rung for {@code state}, or null for {@link StuckState#OK}. Advances the rung.
     */
    public UnstuckAction next(StuckState state) {
        UnstuckAction[] rungs = RUNGS.get(state);
        if (rungs == null) {
            return null;
        }
        int index = _nextRung[state.ordinal()];
        if (index < rungs.length - 1) {
            _nextRung[state.ordinal()]++;
        }
        return rungs[index];
    }

    public boolean ready(long tick) {
        return !_fired || tick - _lastFiredTick >= COOLDOWN_TICKS;
    }

    public void markFired(long tick) {
        _fired = true;
        _lastFiredTick = tick;
    }

    public void onProgress() {
        Arrays.fill(_nextRung, 0);
        _fired = false;
    }
}
