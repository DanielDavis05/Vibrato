package dev.vibrato.trackers;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlockCacheIndexTest {
    @Test
    void reinsertsAPurgedPositionDespiteStaleReverseIndex() {
        Map<String, List<Integer>> byBlock = new HashMap<>();
        Map<Integer, String> byPosition = new HashMap<>();
        BlockCacheIndex.add(byBlock, byPosition, "lava", 1);
        byBlock.get("lava").clear();
        BlockCacheIndex.add(byBlock, byPosition, "lava", 1);
        assertEquals(List.of(1), byBlock.get("lava"));
    }

    @Test
    void reversePruningDoesNotDuplicateAndChangedTypeRemovesOldEntry() {
        Map<String, List<Integer>> byBlock = new HashMap<>();
        Map<Integer, String> byPosition = new HashMap<>();
        BlockCacheIndex.add(byBlock, byPosition, "water", 1);
        byPosition.clear();
        BlockCacheIndex.add(byBlock, byPosition, "water", 1);
        assertEquals(List.of(1), byBlock.get("water"));
        BlockCacheIndex.add(byBlock, byPosition, "lava", 1);
        assertFalse(byBlock.containsKey("water"));
        assertEquals(List.of(1), byBlock.get("lava"));
        assertEquals("lava", byPosition.get(1));
    }
}
