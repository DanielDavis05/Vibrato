package dev.vibrato.tasks.life;

/**
 * Experience point arithmetic from the vanilla experience curve, for levels 0 to {@link #MAX_LEVEL}.
 */
public final class XpMath {

    public static final int MAX_LEVEL = 50;

    private XpMath() {}

    /** Total experience points needed to reach a level from zero. */
    public static int totalXpForLevel(int level) {
        checkLevel(level);
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (5 * level * level - 81 * level) / 2 + 360;
        }
        return (9 * level * level - 325 * level) / 2 + 2220;
    }

    /** Experience points needed to go from {@code level} to {@code level + 1}. */
    public static int xpToNextLevel(int level) {
        checkLevel(level);
        if (level == MAX_LEVEL) {
            throw new IllegalArgumentException("Level " + level + " is the maximum");
        }
        return totalXpForLevel(level + 1) - totalXpForLevel(level);
    }

    /** The level reached with this many total experience points, capped at {@link #MAX_LEVEL}. */
    public static int levelForTotalXp(int totalXp) {
        if (totalXp < 0) {
            throw new IllegalArgumentException("Experience cannot be negative: " + totalXp);
        }
        int level = 0;
        while (level < MAX_LEVEL && totalXpForLevel(level + 1) <= totalXp) {
            level++;
        }
        return level;
    }

    private static void checkLevel(int level) {
        if (level < 0 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("Level must be in [0, " + MAX_LEVEL + "]: " + level);
        }
    }
}
