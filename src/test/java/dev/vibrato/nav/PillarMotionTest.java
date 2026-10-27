package dev.vibrato.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PillarMotionTest {

    @Test
    void pillaringUpOneBlockEveryTwelveTicksIsNotStuck() {
        StuckDetector detector = new StuckDetector(StuckConfig.defaults());
        StuckState state = StuckState.OK;
        double y = 64;
        for (long tick = 0; tick < 400; tick++) {
            if (tick % 12 == 0) {
                y += 1;
            }
            state = detector.update(new MotionSample(tick, 10.5, y, 10.5, 0f, 0f, tick % 12 < 2, false, false,
                    false, true, MotionSample.FULL_AIR, false));
            assertEquals(StuckState.OK, state, "tick " + tick);
        }
    }
}
