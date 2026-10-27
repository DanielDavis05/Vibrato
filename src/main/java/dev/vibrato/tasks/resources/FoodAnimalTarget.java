package dev.vibrato.tasks.resources;

/** Absolute inventory target for one additional animal drop. */
final class FoodAnimalTarget {
    private FoodAnimalTarget() { }

    static int nextCount(int held) {
        return held == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, held) + 1;
    }
}
