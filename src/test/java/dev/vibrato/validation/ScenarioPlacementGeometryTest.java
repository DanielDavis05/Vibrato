package dev.vibrato.validation;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioPlacementGeometryTest {
    private final BlockPos support = new BlockPos(1, 74, 0), target = support.up();
    private final Vec3d eye = new Vec3d(.5, 76.62, .5);
    private final Box player = new Box(.2, 75, .2, .8, 76.8, .8);
    @Test void visibleTopFaceSupportsPlacementWithoutFullSolidBlockAssumption() {
        var hit = new BlockHitResult(new Vec3d(1.5, 75, .5), Direction.UP, support, false);
        assertTrue(ConductorScenarioTask.placementHitIsValid(hit, target, player, eye, 4.5));
    }
    @Test void obstructedWrongFaceFarAndPlayerIntersectingTargetsAreRejected() {
        var top = new BlockHitResult(new Vec3d(1.5, 75, .5), Direction.UP, support, false);
        assertFalse(ConductorScenarioTask.placementHitIsValid(top, support, player, eye, 4.5));
        assertFalse(ConductorScenarioTask.placementHitIsValid(top, target, player, eye, .5));
        assertFalse(ConductorScenarioTask.placementHitIsValid(top, target, new Box(target), eye, 4.5));
        assertFalse(ConductorScenarioTask.placementHitIsValid(new BlockHitResult(top.getPos(), Direction.WEST, support, false),
                target, player, eye, 4.5));
        assertFalse(ConductorScenarioTask.placementHitIsValid(BlockHitResult.createMissed(top.getPos(), Direction.UP, support),
                target, player, eye, 4.5));
    }
}
