package dev.vibrato.tasks.resources;

/** Decisions independent of Minecraft state for the sheep-shearing activity. */
final class ShearWoolPolicy {
    private ShearWoolPolicy() {}

    static int targetCount(int requested, int existing) {
        return Math.max(requested, existing + 1);
    }

    static boolean ready(boolean alive, boolean baby, boolean shearable, boolean sheared) {
        return alive && !baby && shearable && !sheared;
    }

    static boolean finished(boolean confirmedShear, int wool, int target) {
        return confirmedShear && wool >= target;
    }
}
