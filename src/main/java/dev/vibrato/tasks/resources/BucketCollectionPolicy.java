package dev.vibrato.tasks.resources;

import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Inventory accounting for a filled-bucket collection goal. */
final class BucketCollectionPolicy {
    private BucketCollectionPolicy() { }

    static <T> int countMain(Iterable<T> main, Predicate<T> matches, ToIntFunction<T> count) {
        int total = 0;
        for (T stack : main) if (matches.test(stack)) total += count.applyAsInt(stack);
        return total;
    }

    static boolean goalMet(int target, int filledInMain) {
        return filledInMain >= target;
    }

    static int emptyBucketGoal(int target, int filledInMain) {
        return Math.max(0, target - filledInMain);
    }
}
