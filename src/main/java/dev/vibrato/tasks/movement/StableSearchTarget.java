package dev.vibrato.tasks.movement;

import java.util.List;
import java.util.function.Supplier;

/** Keeps a search frontier target until it leaves the set of pending targets. */
final class StableSearchTarget<T> {
    private T _target;

    T select(List<T> pending, Supplier<T> choose) {
        if (_target == null || !pending.contains(_target)) {
            _target = choose.get();
        }
        return _target;
    }
}
