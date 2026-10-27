package dev.vibrato.tasks.combat;

/** Limits opportunistic encounters so navigation can resume promptly. */
public final class TransitCombatPolicy {
    private static final int MAX_TICKS = 120;
    private static final int COOLDOWN_TICKS = 100;

    private TransitCombatPolicy() {}

    public static boolean shouldEscapeDanger(boolean combatTargetIsHostile) {
        return !combatTargetIsHostile;
    }

    public static boolean eligible(boolean alive, double distanceSquared, boolean visible) {
        return alive && visible && distanceSquared <= 25;
    }

    public static boolean keepEngaging(boolean alive, double distanceSquared, int elapsedTicks) {
        return alive && distanceSquared <= 64 && elapsedTicks >= 0 && elapsedTicks < MAX_TICKS;
    }

    public static boolean mayStart(int now, int lastEnded) {
        return lastEnded == Integer.MIN_VALUE || now < lastEnded || (long) now - lastEnded >= COOLDOWN_TICKS;
    }
}
