package dev.vibrato.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductiveMotionTest {

    private static MotionSample sample(long tick, boolean productive) {
        return new MotionSample(tick, 10, 64, 10, 0f, 0f, true, false, false, false, true, MotionSample.FULL_AIR, productive);
    }

    @Test
    void breakingForTwoHundredTicksWithoutMovingStaysOk() {
        StuckConfig config = StuckConfig.defaults();
        StuckDetector detector = new StuckDetector(config);
        StuckState state = StuckState.OK;
        for (long tick = 0; tick < 200; tick++) {
            state = detector.update(sample(tick, true));
        }
        assertEquals(StuckState.OK, state);
    }

    @Test
    void standingStillWhileBreakingOrCalculatingIsNotStuck() {
        StuckConfig config = StuckConfig.defaults();
        StuckDetector detector = new StuckDetector(config);
        StuckState state = StuckState.OK;
        for (long tick = 0; tick < config.stationaryTicks() + 5; tick++) {
            state = detector.update(sample(tick, true));
        }
        assertEquals(StuckState.OK, state);
    }

    @Test
    void movementGetsFreshWindowAfterWorkEnds() {
        StuckDetector detector = new StuckDetector(StuckConfig.defaults());
        for (long tick = 0; tick < 200; tick++) detector.update(sample(tick, true));
        for (long tick = 200; tick < 239; tick++) {
            assertEquals(StuckState.OK, detector.update(sample(tick, false)), "tick " + tick);
        }
        assertEquals(StuckState.STATIONARY_WITH_INTENT, detector.update(sample(239, false)));
    }

    @Test
    void standingStillWithoutWorkIsStillStuck() {
        StuckConfig config = StuckConfig.defaults();
        StuckDetector detector = new StuckDetector(config);
        StuckState state = StuckState.OK;
        for (long tick = 0; tick < config.stationaryTicks() + 5; tick++) {
            state = detector.update(sample(tick, false));
        }
        assertEquals(StuckState.STATIONARY_WITH_INTENT, state);
    }
}
