package dev.vibrato.tasks.construction;

import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Inventory and frame accounting for construction materials. */
final class CastMaterialPolicy {
    private CastMaterialPolicy() { }

    static <T> int countUsable(Iterable<T> mainInventory, Predicate<T> usable, ToIntFunction<T> count) {
        int result = 0;
        for (T stack : mainInventory) if (usable.test(stack)) result += count.applyAsInt(stack);
        return result;
    }

    static int cobblestoneTarget(int existingCountWithoutCursor, int missing, int usable) {
        return existingCountWithoutCursor + Math.max(0, missing - usable);
    }
}
