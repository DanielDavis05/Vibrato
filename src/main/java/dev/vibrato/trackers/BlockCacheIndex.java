package dev.vibrato.trackers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Keeps both block-cache indexes consistent when either has been trimmed. */
final class BlockCacheIndex {
    private BlockCacheIndex() { }

    static <B, P> void add(Map<B, List<P>> byBlock, Map<P, B> byPosition, B block, P pos) {
        B previous = byPosition.get(pos);
        if (previous != null && !Objects.equals(previous, block)) {
            List<P> old = byBlock.get(previous);
            if (old != null) {
                old.remove(pos);
                if (old.isEmpty()) byBlock.remove(previous);
            }
        }
        List<P> positions = byBlock.computeIfAbsent(block, ignored -> new ArrayList<>());
        if (!positions.contains(pos)) positions.add(pos);
        byPosition.put(pos, block);
    }
}
