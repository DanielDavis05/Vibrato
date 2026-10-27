package dev.vibrato.util.helpers;

import baritone.api.pathing.goals.GoalYLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaritoneHelperTest {
    @Test void verticalOffsetMatchesGoalHeuristicInBothDirectionsAndNegativeHeights() {
        check(7, 63);
        check(63, 7);
        check(-63.3, -64.8);
        check(-64.8, -63.3);
        check(-0.2, 0.1);
    }

    @Test void equalVerticalDistanceUsesDifferentClimbAndFallCosts() {
        double up = GoalYLevel.calculate(0, BaritoneHelper.verticalDelta(7, 8));
        double down = GoalYLevel.calculate(0, BaritoneHelper.verticalDelta(8, 7));
        assertTrue(up > 0);
        assertTrue(down > 0);
        org.junit.jupiter.api.Assertions.assertNotEquals(up, down);
    }

    private static void check(double start, double target) {
        int startBlock = (int) Math.floor(start);
        int targetBlock = (int) Math.floor(target);
        double expected = new GoalYLevel(targetBlock).heuristic(0, startBlock, 0);
        assertEquals(expected, GoalYLevel.calculate(0, BaritoneHelper.verticalDelta(start, target)), 1e-9);
    }
}
