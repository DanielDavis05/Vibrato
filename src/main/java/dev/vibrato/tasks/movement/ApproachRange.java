package dev.vibrato.tasks.movement;

/** GoalNear squares its integer radius, so keep it nonnegative and representable. */
final class ApproachRange {
    private static final int MAX_RANGE = 46340;
    private ApproachRange() {}

    static int initial(int distance) { return next(distance); }
    static int next(int distance) { return Math.min(MAX_RANGE, Math.max(0, distance - 1)); }
}
