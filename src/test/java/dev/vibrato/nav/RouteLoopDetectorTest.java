package dev.vibrato.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouteLoopDetectorTest {
    private static MotionSample sample(long tick, double x, double y, double z, boolean productive) {
        return new MotionSample(tick, x, y, z, 0f, 0f, true, false, false,
                false, true, MotionSample.FULL_AIR, productive);
    }

    @Test
    void sustainedThreeCellCircuitTriggersRecoveryOnlyAfterTheTimeThreshold() {
        StuckDetector detector = new StuckDetector(StuckConfig.defaults());
        StuckState state = StuckState.OK;
        for (int tick = 0; tick <= 200; tick++) {
            int phase = tick / 10 % 3;
            state = detector.update(sample(tick, phase + 0.5, 64, 0.5, false));
            if (tick < 160) assertNotEquals(StuckState.OSCILLATING, state);
        }
        assertEquals(StuckState.OSCILLATING, state);
    }

    @Test
    void singleReturnDetourAndChangingRouteDoNotCount() {
        RouteLoopDetector detector = new RouteLoopDetector();
        int[] cells = {0, 1, 2, 1, 0, 3, 4, 5, 6, 7, 8, 9};
        for (int i = 0; i < cells.length; i++) {
            assertFalse(detector.update(sample(i * 30, cells[i] + 0.5, 64, 0.5, false)));
        }
    }

    @Test
    void verticalPillarAndTurnOnlyChangesAreNotCircuits() {
        RouteLoopDetector pillar = new RouteLoopDetector();
        RouteLoopDetector turning = new RouteLoopDetector();
        for (int tick = 0; tick < 500; tick++) {
            assertFalse(pillar.update(sample(tick, 0.5, 64 + tick / 10, 0.5, false)));
            assertFalse(turning.update(sample(tick, 0.5, 64, 0.5, false)));
        }
    }

    @Test
    void productiveTicksClearTheLoopHistory() {
        StuckDetector detector = new StuckDetector(StuckConfig.defaults());
        for (int tick = 0; tick < 500; tick++) {
            int phase = tick / 10 % 3;
            assertNotEquals(StuckState.OSCILLATING,
                    detector.update(sample(tick, phase + 0.5, 64, 0.5, tick % 100 == 0)));
        }
    }
}
