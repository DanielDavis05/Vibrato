package dev.vibrato.tasks.construction;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaceBlockTaskGeometryTest {
    @Test void acceptsOnlyTheRequestedSupportFaceLeadingToTarget() {
        BlockPos support = new BlockPos(-5, 80, 19);
        BlockPos target = support.up();
        BlockHitResult top = new BlockHitResult(Vec3d.ofCenter(support), Direction.UP, support, false);
        assertTrue(PlaceBlockTask.hitPlacesAt(top, support, Direction.UP, target));
        assertFalse(PlaceBlockTask.hitPlacesAt(top, support, Direction.NORTH, target));
        assertFalse(PlaceBlockTask.hitPlacesAt(top, support.east(), Direction.UP, target));
        assertFalse(PlaceBlockTask.hitPlacesAt(top, support, Direction.UP, target.east()));
        BlockHitResult miss = BlockHitResult.createMissed(Vec3d.ofCenter(support), Direction.UP, support);
        assertFalse(PlaceBlockTask.hitPlacesAt(miss, support, Direction.UP, target));
    }
}
