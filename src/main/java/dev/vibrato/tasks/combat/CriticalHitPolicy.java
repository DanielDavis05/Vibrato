package dev.vibrato.tasks.combat;

/** Wait briefly for a jump's descending phase, then strike even if it failed. */
public final class CriticalHitPolicy {
    private CriticalHitPolicy() {}

    public static boolean waitForFall(int elapsedTicks, boolean grounded, float fallDistance) {
        return elapsedTicks >= 0 && elapsedTicks < 12 && (grounded || fallDistance <= 0);
    }
}
