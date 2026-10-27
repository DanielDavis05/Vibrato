package dev.vibrato.tasks.construction;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlaceBlockNearbyTaskTest {
    @Test
    void rejectsOccupiedCellsButAllowsAdjacentAndSupportingBlocks() {
        // A player standing within the x=-1 cell.
        Box player = new Box(-0.81, 105, 0.20, -0.21, 106.8, 0.80);
        assertTrue(PlaceBlockNearbyTask.overlapsPlayer(player, new BlockPos(-1, 105, 0)));
        assertTrue(PlaceBlockNearbyTask.overlapsPlayer(player, new BlockPos(-1, 106, 0)));
        assertFalse(PlaceBlockNearbyTask.overlapsPlayer(player, new BlockPos(0, 105, 0)));
        assertFalse(PlaceBlockNearbyTask.overlapsPlayer(player, new BlockPos(-1, 104, 0)));

        Box straddling = new Box(-0.3, 105, 0.2, 0.3, 106.8, 0.8);
        assertTrue(PlaceBlockNearbyTask.overlapsPlayer(straddling, new BlockPos(-1, 105, 0)));
        assertTrue(PlaceBlockNearbyTask.overlapsPlayer(straddling, new BlockPos(0, 105, 0)));
    }
}
