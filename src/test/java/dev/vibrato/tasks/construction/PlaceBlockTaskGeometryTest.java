package dev.vibrato.tasks.construction;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaceBlockTaskGeometryTest {
    @Test void standingBelowTheTopFaceCannotPlaceButScaffoldHeightCan() {
        Vec3d face = new Vec3d(42.5, 76, -102.5);
        Vec3d normal = new Vec3d(0, 1, 0);
        assertFalse(PlaceBlockTask.faceReachable(new Vec3d(41.5, 75.27, -103.5), face, normal, 4.5));
        assertTrue(PlaceBlockTask.faceReachable(new Vec3d(41.5, 76.27, -103.5), face, normal, 4.5));
        assertFalse(PlaceBlockTask.faceReachable(new Vec3d(37.5, 76.27, -103.5), face, normal, 4.5));
    }

    @Test void worldSevenOuterScaffoldPositionIsWithinPhysicalReach() {
        Vec3d eye = new Vec3d(43.5, 76.27, -106.5);
        assertTrue(PlaceBlockTask.faceReachable(eye, new Vec3d(42.5, 76, -102.5), new Vec3d(0, 1, 0), 4.5));
    }

    @Test void twoBlockScaffoldRequiresSolidGroundAndBothClearCells() {
        BlockPos base = new BlockPos(41, 73, -104);
        assertTrue(PlaceBlockTask.scaffoldSiteValid(base, 2, p -> true, p -> p.equals(base.down())));
        assertFalse(PlaceBlockTask.scaffoldSiteValid(base, 2, p -> !p.equals(base.up()), p -> true));
        assertFalse(PlaceBlockTask.scaffoldSiteValid(base, 2, p -> true, p -> false));
        assertFalse(PlaceBlockTask.scaffoldSiteValid(base, 3, p -> true, p -> true));
    }

    @Test void localSandIsPreferredButCannotBePlacedAfterFoundationRemoval() {
        assertEquals(PlaceBlockTask.ScaffoldMaterial.SAND, PlaceBlockTask.chooseScaffoldMaterial(true));
        assertEquals(PlaceBlockTask.ScaffoldMaterial.DIRT, PlaceBlockTask.chooseScaffoldMaterial(false));
        BlockPos base = new BlockPos(43, 74, -107);
        assertTrue(PlaceBlockTask.scaffoldFoundationIntact(base, p -> p.equals(base.down())));
        assertFalse(PlaceBlockTask.scaffoldFoundationIntact(base, p -> false));
    }

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
