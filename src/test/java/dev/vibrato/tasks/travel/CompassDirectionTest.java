package dev.vibrato.tasks.travel;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompassDirectionTest {

    private static final BlockPos ORIGIN = new BlockPos(100, 70, 100);

    @Test
    void cardinalOffsetsMoveAlongOneAxis() {
        assertEquals(new BlockPos(100, 70, 50), CompassDirection.N.offset(ORIGIN, 50));
        assertEquals(new BlockPos(150, 70, 100), CompassDirection.E.offset(ORIGIN, 50));
        assertEquals(new BlockPos(100, 70, 150), CompassDirection.S.offset(ORIGIN, 50));
        assertEquals(new BlockPos(50, 70, 100), CompassDirection.W.offset(ORIGIN, 50));
    }

    @Test
    void diagonalOffsetsHaveLengthOfTheDistance() {
        BlockPos ne = CompassDirection.NE.offset(ORIGIN, 100);
        double length = Math.sqrt(Math.pow(ne.getX() - ORIGIN.getX(), 2) + Math.pow(ne.getZ() - ORIGIN.getZ(), 2));
        assertEquals(100.0, length, 1.0);
        assertEquals(ORIGIN.getY(), ne.getY());
    }

    @Test
    void everyDirectionKeepsHeight() {
        for (CompassDirection direction : CompassDirection.values()) {
            assertEquals(ORIGIN.getY(), direction.offset(ORIGIN, 40).getY(), direction.name());
        }
    }
}
