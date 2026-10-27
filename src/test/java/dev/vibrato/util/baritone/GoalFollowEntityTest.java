package dev.vibrato.util.baritone;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GoalFollowEntityTest {
    @Test
    void surfaceNextToDropAtBlockEdgeIsInRange() {
        GoalFollowEntity goal = new GoalFollowEntity(() -> new Vec3d(10.98, 130.999, 10.5), 1);
        assertTrue(goal.isInGoal(10, 131, 10));
        assertEquals(0, goal.heuristic(10, 131, 10));
        assertTrue(goal.isInGoal(11, 131, 10));
        assertEquals(0, goal.heuristic(11, 131, 10));
    }

    @Test
    void zeroRadiusStillAcceptsOnlyTheEntityBlock() {
        GoalFollowEntity goal = new GoalFollowEntity(() -> new Vec3d(-0.01, 130.999, 5.9), 0);
        assertTrue(goal.isInGoal(-1, 130, 5));
        assertEquals(0, goal.heuristic(-1, 130, 5));
        assertFalse(goal.isInGoal(-1, 131, 5));
        assertFalse(goal.isInGoal(0, 130, 5));
    }

    @Test
    void followsMovingTargetAndUsesZeroHeuristicForReachedPositions() {
        AtomicReference<Vec3d> position = new AtomicReference<>(new Vec3d(0.5, 64, 0.5));
        GoalFollowEntity goal = new GoalFollowEntity(position::get, 1);
        assertTrue(goal.isInGoal(0, 64, 0));
        assertFalse(goal.isInGoal(10, 64, 0));
        position.set(new Vec3d(10.5, 64, 0.5));
        assertFalse(goal.isInGoal(0, 64, 0));
        assertTrue(goal.isInGoal(10, 64, 0));
        assertEquals(0, goal.heuristic(10, 64, 0));
    }
}
