package dev.vibrato.tasks.travel;

import dev.vibrato.tasks.travel.WaterCrossingPlanner.Crossing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaterCrossingPlannerTest {

    @Test
    void shortGapIsSwumEvenWithABoat() {
        assertEquals(Crossing.SWIM, WaterCrossingPlanner.plan(0, true, 0));
        assertEquals(Crossing.SWIM, WaterCrossingPlanner.plan(WaterCrossingPlanner.SWIM_LIMIT, true, 64));
    }

    @Test
    void longGapWithBoatTakesTheBoat() {
        assertEquals(Crossing.BOAT, WaterCrossingPlanner.plan(WaterCrossingPlanner.SWIM_LIMIT + 1, true, 0));
    }

    @Test
    void longGapWithoutBoatBridgesWhenThereIsOneStack() {
        assertEquals(Crossing.BRIDGE, WaterCrossingPlanner.plan(40, false, WaterCrossingPlanner.BRIDGE_BLOCK_MINIMUM));
    }

    @Test
    void longGapWithNoBoatAndFewBlocksSwimsAround() {
        assertEquals(Crossing.SWIM_AROUND, WaterCrossingPlanner.plan(40, false, WaterCrossingPlanner.BRIDGE_BLOCK_MINIMUM - 1));
    }

    @Test
    void countsOnlyWaterStrictlyBetweenEndpoints() {
        BlockPos from = new BlockPos(0, 64, 0);
        BlockPos to = new BlockPos(10, 64, 0);
        // Every block from x=1..9 is water.
        int count = WaterCrossingPlanner.countWaterOnLine(from, to, p -> true);
        assertEquals(9, count);
    }

    @Test
    void countsDiagonalLineAlongTheLongerAxis() {
        BlockPos from = new BlockPos(0, 64, 0);
        BlockPos to = new BlockPos(4, 64, 4);
        int count = WaterCrossingPlanner.countWaterOnLine(from, to, p -> p.getX() >= 2);
        // Intermediate samples (1,1), (2,2), (3,3): x>=2 for two of them.
        assertEquals(2, count);
    }

    @Test
    void noWaterOnLandLine() {
        assertEquals(0, WaterCrossingPlanner.countWaterOnLine(new BlockPos(0, 64, 0), new BlockPos(50, 64, 0), p -> false));
    }
}
