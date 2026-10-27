package dev.vibrato.tasks.explore;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryLogTest {

    @BeforeEach
    void clearLog() {
        DiscoveryLog.clear();
    }

    @Test
    void addStoresNewDiscovery() {
        assertTrue(DiscoveryLog.add("village", new BlockPos(0, 64, 0)));
        assertEquals(1, DiscoveryLog.all("village").size());
    }

    @Test
    void sameKindWithinSixteenBlocksIsDeduped() {
        assertTrue(DiscoveryLog.add("village", new BlockPos(0, 64, 0)));
        assertFalse(DiscoveryLog.add("village", new BlockPos(10, 64, 10)));
        assertEquals(1, DiscoveryLog.all("village").size());
    }

    @Test
    void sameKindBeyondSixteenBlocksIsKept() {
        assertTrue(DiscoveryLog.add("village", new BlockPos(0, 64, 0)));
        assertTrue(DiscoveryLog.add("village", new BlockPos(100, 64, 0)));
        assertEquals(2, DiscoveryLog.all("village").size());
    }

    @Test
    void differentKindAtSamePlaceIsKept() {
        assertTrue(DiscoveryLog.add("village", new BlockPos(0, 64, 0)));
        assertTrue(DiscoveryLog.add("structure:desert_temple", new BlockPos(0, 64, 0)));
    }

    @Test
    void nearestPicksClosestOfKind() {
        DiscoveryLog.add("village", new BlockPos(200, 64, 0));
        DiscoveryLog.add("village", new BlockPos(-50, 64, 0));
        DiscoveryLog.add("structure:desert_temple", new BlockPos(5, 64, 0));
        Optional<BlockPos> nearest = DiscoveryLog.nearest("village", new BlockPos(0, 64, 0));
        assertEquals(new BlockPos(-50, 64, 0), nearest.orElseThrow());
    }

    @Test
    void nearestIsEmptyForUnknownKind() {
        assertTrue(DiscoveryLog.nearest("biome:plains", BlockPos.ORIGIN).isEmpty());
    }

    @Test
    void clearForgetsEverything() {
        DiscoveryLog.add("village", BlockPos.ORIGIN);
        DiscoveryLog.clear();
        assertTrue(DiscoveryLog.all("village").isEmpty());
    }
}
