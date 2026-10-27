package dev.vibrato.human.record;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EdgeProbeTest {

    private static final int FEET = 64; // standing feet block: the standing surface is block 63
    private static final int GROUND = 63; // solid block index whose top is at feet level
    private static final double EPS = 1e-9;

    /** Ground top at {@code GROUND} for x below {@code dropAt}, then {@code lowerTop} (or NONE) from there on. */
    private static EdgeProbe.GroundLookup stepAt(int dropAt, int lowerTop) {
        return (x, z) -> x < dropAt ? GROUND : lowerTop;
    }

    private static EdgeProbe.GroundLookup flat() {
        return (x, z) -> GROUND;
    }

    @Test
    void flatGroundHasNoEdge() {
        assertNull(EdgeProbe.distanceToEdge(0.5, FEET, 0.5, 1, 0, flat()));
    }

    @Test
    void ledgeAheadIsReportedAtTheFirstVoidColumn() {
        // Ground for x <= 3, void from x = 4. Origin x = 0.5 walking +x: sample at d = 3.5 reaches x = 4.0.
        Double edge = EdgeProbe.distanceToEdge(0.5, FEET, 0.5, 1, 0, stepAt(4, EdgeProbe.NONE));

        assertEquals(3.5, edge, EPS);
    }

    @Test
    void oneBlockStepDownIsNotAnEdge() {
        // Ground top at 62, so the standing surface is 63 = feet - 1, which is not lower than feet - 1.
        assertNull(EdgeProbe.distanceToEdge(0.5, FEET, 0.5, 1, 0, stepAt(4, GROUND - 1)));
    }

    @Test
    void twoBlockDropIsAnEdge() {
        // Ground top at 61, so the standing surface is 62 < feet - 1.
        Double edge = EdgeProbe.distanceToEdge(0.5, FEET, 0.5, 1, 0, stepAt(4, GROUND - 2));

        assertEquals(3.5, edge, EPS);
    }

    @Test
    void oneBlockStepUpIsNotAnEdge() {
        // Ground top at 64 (one block above the standing surface): higher ground is never a drop.
        assertNull(EdgeProbe.distanceToEdge(0.5, FEET, 0.5, 1, 0, stepAt(4, GROUND + 1)));
    }

    @Test
    void standingLevelDecidesWhetherFlatGroundIsAnEdge() {
        // Over flat ground (top 63), a standing level of 64 is not an edge, but a feet level of 66 (what an airborne
        // player's height would give) is: the caller must pass the grounded level, not the current height.
        assertNull(EdgeProbe.distanceToEdge(0.5, 64, 0.5, 1, 0, flat()));
        assertEquals(0.0, EdgeProbe.distanceToEdge(0.5, 66, 0.5, 1, 0, flat()), EPS);
    }

    @Test
    void probeRunsOnlyOnOpenGround() {
        assertTrue(EdgeProbe.canProbe(true, false, false, false, false));
    }

    @Test
    void airborneTicksAreNotProbed() {
        assertFalse(EdgeProbe.canProbe(false, false, false, false, false));
    }

    @Test
    void waterLavaVehiclesAndLaddersAreNotProbed() {
        assertFalse(EdgeProbe.canProbe(true, true, false, false, false));
        assertFalse(EdgeProbe.canProbe(true, false, true, false, false));
        assertFalse(EdgeProbe.canProbe(true, false, false, true, false));
        assertFalse(EdgeProbe.canProbe(true, false, false, false, true));
    }

    @Test
    void voidGapFollowedByGroundIsReportedAtItsStart() {
        // Void for columns 3 and 4 only. Origin x = 0.5 walking +x: sample at d = 2.5 reaches x = 3.0.
        EdgeProbe.GroundLookup gap = (x, z) -> (x == 3 || x == 4) ? EdgeProbe.NONE : GROUND;

        assertEquals(2.5, EdgeProbe.distanceToEdge(0.5, FEET, 0.5, 1, 0, gap), EPS);
    }

    @Test
    void edgeBeyondSixBlocksIsAbsent() {
        // Void from x = 7: the first void column is reached at d = 6.5, past the 6 block range.
        assertNull(EdgeProbe.distanceToEdge(0.5, FEET, 0.5, 1, 0, stepAt(7, EdgeProbe.NONE)));
    }

    @Test
    void edgeExactlyAtSixBlocksIsIncluded() {
        // Void from x = 6: origin 0.5 reaches column 6 at d = 5.5, inside the range.
        assertEquals(5.5, EdgeProbe.distanceToEdge(0.5, FEET, 0.5, 1, 0, stepAt(6, EdgeProbe.NONE)), EPS);
    }

    @Test
    void edgeUnderTheFeetIsReportedAtZero() {
        // Column 0 (the player's own column) has no ground.
        assertEquals(0.0, EdgeProbe.distanceToEdge(0.5, FEET, 0.5, 1, 0, (x, z) -> EdgeProbe.NONE), EPS);
    }

    @Test
    void unloadedColumnsAreSkippedNotTreatedAsEdges() {
        // Columns 2 and 3 are unloaded, the rest is flat ground.
        EdgeProbe.GroundLookup partlyLoaded = (x, z) -> (x == 2 || x == 3) ? EdgeProbe.UNKNOWN : GROUND;

        assertNull(EdgeProbe.distanceToEdge(0.5, FEET, 0.5, 1, 0, partlyLoaded));
    }

    @Test
    void unloadedColumnsDoNotHideALaterEdge() {
        // Columns 2 and 3 are unloaded, void from column 4.
        EdgeProbe.GroundLookup lookup = (x, z) -> {
            if (x == 2 || x == 3) {
                return EdgeProbe.UNKNOWN;
            }
            return x < 4 ? GROUND : EdgeProbe.NONE;
        };

        assertEquals(3.5, EdgeProbe.distanceToEdge(0.5, FEET, 0.5, 1, 0, lookup), EPS);
    }

    @Test
    void diagonalDirectionUsesBothAxes() {
        // Void for columns with x >= 3 only. Heading (1, 1) from (0.5, 0.5): x = 0.5 + d / sqrt(2) first reaches 3
        // between samples 3.5 (x = 2.97) and 3.75 (x = 3.15).
        EdgeProbe.GroundLookup voidFromX3 = (x, z) -> x >= 3 ? EdgeProbe.NONE : GROUND;

        assertEquals(3.75, EdgeProbe.distanceToEdge(0.5, FEET, 0.5, 1, 1, voidFromX3), EPS);
    }

    @Test
    void diagonalDirectionIsNormalised() {
        EdgeProbe.GroundLookup voidFromX3 = (x, z) -> x >= 3 ? EdgeProbe.NONE : GROUND;

        assertEquals(EdgeProbe.distanceToEdge(0.5, FEET, 0.5, 1, 1, voidFromX3),
                EdgeProbe.distanceToEdge(0.5, FEET, 0.5, 5, 5, voidFromX3));
    }

    @Test
    void zeroDirectionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EdgeProbe.distanceToEdge(0.5, FEET, 0.5, 0, 0, flat()));
    }

    @Test
    void zeroVelocityHeadingFollowsYawSouthAndWest() {
        // Yaw 0 faces +z; yaw 90 faces -x.
        double[] south = EdgeProbe.heading(0, 0, 0);
        assertEquals(0.0, south[0], EPS);
        assertEquals(1.0, south[1], EPS);

        double[] west = EdgeProbe.heading(0, 0, 90);
        assertEquals(-1.0, west[0], EPS);
        assertEquals(0.0, west[1], EPS);
    }

    @Test
    void nearlyStillPlayerUsesYaw() {
        double[] heading = EdgeProbe.heading(0.001, 0, 90);

        assertEquals(-1.0, heading[0], EPS);
        assertEquals(0.0, heading[1], EPS);
    }

    @Test
    void movingPlayerUsesVelocityNotYaw() {
        double[] heading = EdgeProbe.heading(0.2, 0, 180);

        assertEquals(1.0, heading[0], EPS);
        assertEquals(0.0, heading[1], EPS);
    }

    @Test
    void headingIsAUnitVector() {
        double[] heading = EdgeProbe.heading(0.3, -0.4, 0);

        assertEquals(1.0, Math.hypot(heading[0], heading[1]), EPS);
    }
}
