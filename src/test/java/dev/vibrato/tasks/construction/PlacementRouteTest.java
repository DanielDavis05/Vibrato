package dev.vibrato.tasks.construction;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PlacementRouteTest {
    @Test void rejectsTwoBlockCliffAndAcceptsAnActualIntermediateStep() {
        BlockPos start = new BlockPos(0, 1, 0), goal = new BlockPos(2, 3, 0);
        Set<BlockPos> cliff = Set.of(start.down(), new BlockPos(1, 0, 0), goal.down());
        assertFalse(PlacementRoute.reachable(start, goal, p -> !cliff.contains(p), cliff::contains));
        Set<BlockPos> steps = Set.of(start.down(), new BlockPos(1, 1, 0), goal.down());
        assertTrue(PlacementRoute.reachable(start, goal, p -> !steps.contains(p), steps::contains));
    }

    @Test void siteTransitPrecedesLocalSearchForDistantOrUnloadedTarget() {
        assertEquals(PlacementRoute.SiteAction.APPROACH, PlacementRoute.siteAction(true, false, false));
        assertEquals(PlacementRoute.SiteAction.APPROACH, PlacementRoute.siteAction(true, true, false));
        assertEquals(PlacementRoute.SiteAction.WAIT, PlacementRoute.siteAction(false, false, false));
        assertEquals(PlacementRoute.SiteAction.SEARCH, PlacementRoute.siteAction(false, true, false));
        assertEquals(PlacementRoute.SiteAction.FAIL, PlacementRoute.siteAction(true, false, true));
    }

    @Test void airborneWaitsForLandingAndFailsWhenDeadlineExpires() {
        assertEquals(PlacementRoute.GroundAction.WAIT, PlacementRoute.groundAction(false, false, false));
        assertEquals(PlacementRoute.GroundAction.WAIT, PlacementRoute.groundAction(false, true, false));
        assertEquals(PlacementRoute.GroundAction.PROCEED, PlacementRoute.groundAction(true, true, false));
        assertEquals(PlacementRoute.GroundAction.FAIL, PlacementRoute.groundAction(false, true, true));
    }

    @Test void unsupportedOrObstructedStartNeverCountsAsACompletedRoute() {
        BlockPos start = new BlockPos(0, 1, 0);
        assertFalse(PlacementRoute.reachable(start, start, p -> true, p -> false));
        assertFalse(PlacementRoute.reachable(start, start, p -> !p.equals(start.up()), p -> true));
        assertTrue(PlacementRoute.reachable(start, start, p -> true, p -> p.equals(start.down())));
    }

    @Test void riseRequiresHeadClearanceAboveTheStartingCell() {
        BlockPos start = new BlockPos(0, 1, 0), goal = new BlockPos(1, 2, 0);
        Set<BlockPos> floor = Set.of(start.down(), goal.down());
        assertFalse(PlacementRoute.reachable(start, goal,
                p -> !floor.contains(p) && !p.equals(start.up(2)), floor::contains));
        assertTrue(PlacementRoute.reachable(start, goal, p -> !floor.contains(p), floor::contains));
    }

    @Test void anObstructedOrWetIntermediateCellDoesNotFormARoute() {
        BlockPos start = new BlockPos(0, 1, 0), goal = new BlockPos(2, 3, 0);
        Set<BlockPos> steps = Set.of(start.down(), new BlockPos(1, 1, 0), goal.down());
        BlockPos blocked = new BlockPos(1, 2, 0);
        assertFalse(PlacementRoute.reachable(start, goal,
                p -> !steps.contains(p) && !p.equals(blocked), steps::contains));
    }
}
