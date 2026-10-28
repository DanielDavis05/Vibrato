package dev.vibrato.chains;

/** Retain a confirmed active ranged threat briefly while line of sight flickers. */
final class RangedThreatMemory {
    static final double ACQUIRE_DISTANCE_SQ = 24 * 24;
    private static final double RELEASE_DISTANCE_SQ = 30 * 30;
    private static final int MEMORY_TICKS = 20 * 10;
    private RangedThreatMemory() {}

    static boolean shouldDefend(boolean intendedCombat, boolean vulnerable) { return !intendedCombat || vulnerable; }

    static boolean observe(boolean alive, boolean attacking, boolean excluded, double distanceSquared, boolean visible) {
        return alive && attacking && !excluded && visible && distanceSquared <= ACQUIRE_DISTANCE_SQ;
    }
    static boolean retain(boolean alive, boolean excluded, double distanceSquared, int now, int seen) {
        long age = (long) now - seen;
        return alive && !excluded && distanceSquared < RELEASE_DISTANCE_SQ && age >= 0 && age < MEMORY_TICKS;
    }
}
