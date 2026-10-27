package dev.vibrato.tasks.combat;

/** Time limits for combat tasks. Pure: the director calls these while planning, before any task exists. */
public final class CombatBudgets {

    private CombatBudgets() {}

    /** Scales with the kill count; the activity budget uses the same value so the director never cuts a slay short. */
    public static int slayTimeLimitSeconds(int count) {
        return 180 + 40 * count;
    }
}
