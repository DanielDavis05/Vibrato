package dev.vibrato.advancement;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Answers "is this advancement done?" from a snapshot of completed advancement ids. The snapshot comes from a
 * supplier, so the logic stays pure and the client lookup lives in {@link ClientAdvancements}.
 */
public final class AdvancementTracker {

    private final Supplier<Collection<Identifier>> _doneSource;

    public AdvancementTracker(Supplier<Collection<Identifier>> doneSource) {
        _doneSource = Objects.requireNonNull(doneSource, "doneSource");
    }

    public boolean isDone(Identifier id) {
        return snapshot().contains(id);
    }

    public int doneCount() {
        return snapshot().size();
    }

    /** The ids from {@code wanted} that are not done yet, in the order given. */
    public List<Identifier> remainingFrom(Collection<Identifier> wanted) {
        Set<Identifier> done = snapshot();
        return wanted.stream().filter(id -> !done.contains(id)).toList();
    }

    private Set<Identifier> snapshot() {
        return Set.copyOf(_doneSource.get());
    }
}
