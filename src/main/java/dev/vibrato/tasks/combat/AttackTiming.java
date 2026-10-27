package dev.vibrato.tasks.combat;

/** When to swing: the attack cooldown progress at which a hit is worth landing. */
public final class AttackTiming {

    private static final float FULL_SWEEP = 0.9f;

    private AttackTiming() {}

    /**
     * @param cooldownProgress the player's attack cooldown in [0, 1], where 1 is a fully recharged swing
     */
    public static boolean shouldSwing(float cooldownProgress, MobKind kind) {
        return cooldownProgress >= thresholdFor(kind);
    }

    /** Creepers and endermen get a full-cooldown hit, since a missed swing wastes the window to retreat. */
    static float thresholdFor(MobKind kind) {
        return switch (kind) {
            case CREEPER, ENDERMAN -> 1.0f;
            default -> FULL_SWEEP;
        };
    }
}
