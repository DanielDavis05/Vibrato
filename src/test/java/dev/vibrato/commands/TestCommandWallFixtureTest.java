package dev.vibrato.commands;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestCommandWallFixtureTest {
    @Test void originalWallFootprintIncludesTargetAndExcludesAdjacentScaffold() {
        BlockPos target = new BlockPos(42, 76, -103);
        var footprint = TestCommand.wallFootprint(target);
        assertEquals(14, footprint.size());
        assertTrue(footprint.contains(target));
        assertTrue(footprint.contains(new BlockPos(42, 73, -103)));
        assertTrue(footprint.contains(new BlockPos(45, 75, -103)));
        assertFalse(footprint.contains(new BlockPos(42, 73, -104)));
    }
}
