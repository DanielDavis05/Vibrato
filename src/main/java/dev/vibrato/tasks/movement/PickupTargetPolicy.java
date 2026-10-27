package dev.vibrato.tasks.movement;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Keep a valid pickup target until it disappears or is declared unreachable. */
final class PickupTargetPolicy {
    private PickupTargetPolicy() {}

    static <T> Optional<T> select(T current, Predicate<T> valid, Supplier<Optional<T>> nearest) {
        if (current != null && valid.test(current)) return Optional.of(current);
        return nearest.get();
    }
}
