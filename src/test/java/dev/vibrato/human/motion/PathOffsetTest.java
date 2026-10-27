package dev.vibrato.human.motion;

import dev.vibrato.human.motion.PathOffset.Point;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PathOffsetTest {

    /** Wide wander so the corridor clamp is actually exercised. */
    private static final HumanProfile WIDE = new HumanProfile(5, 0, 0, 0, 0, 6, 0.3, 0, 0.5, 0.05, 3, 0.92, 1.2, 1, 1);

    /** Look-ahead 3 blocks, no wander. */
    private static final HumanProfile STEADY = new HumanProfile(5, 0, 0, 0, 0, 6, 0.3, 0, 0.03, 0.1, 3, 0.92, 1.2, 1, 1);

    private static List<Double> track(long seed, double corridor, int ticks) {
        PathOffset path = new PathOffset(seed, WIDE);
        List<Double> out = new ArrayList<>();
        for (int t = 0; t < ticks; t++) {
            out.add(path.step(corridor));
        }
        return out;
    }

    @Test
    void sameSeedGivesSameOffsets() {
        assertEquals(track(3, 0.4, 500), track(3, 0.4, 500));
    }

    @Test
    void offsetStaysInsideCorridorAndWanders() {
        for (long seed = 0; seed < 10; seed++) {
            List<Double> offsets = track(seed, 0.4, 2000);
            for (double offset : offsets) {
                assertTrue(Math.abs(offset) <= 0.4 + 1e-12, "offset " + offset);
            }
            assertTrue(offsets.stream().anyMatch(o -> Math.abs(o) > 0.05), "never wandered for seed " + seed);
        }
    }

    @Test
    void zeroCorridorIsExactlyZero() {
        PathOffset path = new PathOffset(4, WIDE);
        for (int t = 0; t < 500; t++) {
            assertEquals(0.0, path.step(0.0));
            assertEquals(0.0, path.offset());
        }
        assertEquals(0.0, new PathOffset(4, WIDE).step(-1));
    }

    @Test
    void zeroCorridorAimsAlongTheCentreline() {
        PathOffset path = new PathOffset(4, STEADY);
        path.step(0);
        Point aim = path.lookAhead(new Point(0, 0), new Point(1, 0), null);
        assertEquals(3, aim.x(), 1e-9);
        assertEquals(0, aim.z(), 1e-9);
    }

    @Test
    void lookAheadFollowsTheNextNodeThenTheNodeAfterIt() {
        PathOffset path = new PathOffset(4, STEADY);
        Point aim = path.lookAhead(new Point(0, 0), new Point(1, 0), new Point(1, 1));
        assertEquals(1, aim.x(), 1e-9);
        assertEquals(2, aim.z(), 1e-9);
    }

    @Test
    void lookAheadExtendsTheLastLegPastTheEndOfThePath() {
        PathOffset path = new PathOffset(4, STEADY);
        Point aim = path.lookAhead(new Point(0, 0), new Point(1, 0), null);
        assertEquals(3, aim.x(), 1e-9);
    }

    @Test
    void aimIsOffsetSidewaysByTheCurrentOffset() {
        PathOffset path = new PathOffset(8, WIDE);
        double offset = 0;
        for (int t = 0; t < 200 && Math.abs(offset) < 0.1; t++) {
            offset = path.step(0.4);
        }
        Point aim = path.lookAhead(new Point(0, 0), new Point(1, 0), null);
        assertEquals(3, aim.x(), 1e-9);
        assertEquals(offset, aim.z(), 1e-9);
    }
}
