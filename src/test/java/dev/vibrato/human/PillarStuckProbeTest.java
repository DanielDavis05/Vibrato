package dev.vibrato.human;

import dev.vibrato.nav.MotionSample;
import dev.vibrato.nav.StuckConfig;
import dev.vibrato.nav.StuckDetector;
import dev.vibrato.nav.StuckState;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Does the current nav code treat a nerd-pole pillar as stuck? */
class PillarStuckProbeTest {

    private static MotionSample sample(long tick, double y) {
        return new MotionSample(tick, 0.5, y, 0.5, 0f, 80f, false, false, false, false, true, MotionSample.FULL_AIR);
    }

    @Test
    void pillarRisingOneBlockEveryFiveTicksIsNotStuck() {
        StuckDetector detector = new StuckDetector(StuckConfig.defaults());
        double y = 64;
        for (long tick = 0; tick < 120; tick++) {
            if (tick % 5 == 0) {
                y += 1;
            }
            assertEquals(StuckState.OK, detector.update(sample(tick, y)), "tick " + tick);
        }
    }

    @Test
    void standingStillWhilePathingIsStuck() {
        StuckDetector detector = new StuckDetector(StuckConfig.defaults());
        StuckState last = StuckState.OK;
        for (long tick = 0; tick < 60; tick++) {
            last = detector.update(sample(tick, 64));
        }
        assertEquals(StuckState.STATIONARY_WITH_INTENT, last);
    }
}
