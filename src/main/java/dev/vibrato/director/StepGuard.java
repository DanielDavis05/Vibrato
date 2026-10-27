package dev.vibrato.director;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Keeps an exception in one step from taking down the whole client. Used around activity factories and the step task's
 * tick, so a broken activity fails its step and the Director moves on.
 */
public final class StepGuard {

    private StepGuard() {}

    /** @return true if the action completed, false if it threw (the error is handed to {@code onError}) */
    public static boolean run(Runnable action, Consumer<RuntimeException> onError) {
        try {
            action.run();
            return true;
        } catch (RuntimeException e) {
            onError.accept(e);
            return false;
        }
    }

    /** @return the result, or null if it threw (the error is handed to {@code onError}) */
    public static <T> T call(Supplier<T> action, Consumer<RuntimeException> onError) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            onError.accept(e);
            return null;
        }
    }
}
