package dev.vibrato.tasks.movement;

/** The options that determine whether one active wander task can serve another request. */
final class WanderPolicy {
    private WanderPolicy() {}

    static boolean equivalent(float distance, boolean increaseRange, boolean forceExplore,
                              float otherDistance, boolean otherIncreaseRange, boolean otherForceExplore) {
        if (increaseRange != otherIncreaseRange || forceExplore != otherForceExplore) return false;
        if (Float.isInfinite(distance) || Float.isInfinite(otherDistance)) {
            return Float.isInfinite(distance) == Float.isInfinite(otherDistance);
        }
        return Math.abs(distance - otherDistance) < 0.5f;
    }
}
