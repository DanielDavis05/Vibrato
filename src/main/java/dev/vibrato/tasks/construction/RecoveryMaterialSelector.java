package dev.vibrato.tasks.construction;

import java.util.Arrays;
import java.util.function.Predicate;

final class RecoveryMaterialSelector {
    private RecoveryMaterialSelector() { }

    static boolean isProtected(boolean protectedAtInterruption, boolean protectedNow) {
        return protectedAtInterruption || protectedNow;
    }

    static <T> T firstUsable(T[] candidates, Predicate<T> available, Predicate<T> protectedItem) {
        return Arrays.stream(candidates).filter(available).filter(protectedItem.negate()).findFirst().orElse(null);
    }
}
