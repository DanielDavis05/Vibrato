package dev.vibrato.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StuckDetectorTest {

    private final StuckDetector detector = new StuckDetector(StuckConfig.defaults());
    private long tick;

    private MotionSample sample(double x, double z, float yaw, boolean wantsToMove) {
        return new MotionSample(tick++, x, 64, z, yaw, 0f, true, false, false, false, wantsToMove, MotionSample.FULL_AIR, false);
    }

    private MotionSample sample(double x, double z, boolean wantsToMove) {
        return sample(x, z, 0f, wantsToMove);
    }

    @Test
    void standingStillWithIntentBecomesStationaryAfterWindow() {
        for (int i = 0; i < 39; i++) {
            assertEquals(StuckState.OK, detector.update(sample(5.5, 5.5, true)));
        }
        assertEquals(StuckState.STATIONARY_WITH_INTENT, detector.update(sample(5.5, 5.5, true)));
    }

    @Test
    void idleBotIsNeverStuck() {
        for (int i = 0; i < 100; i++) {
            assertEquals(StuckState.OK, detector.update(sample(5.5, 5.5, false)));
        }
    }

    @Test
    void steadyWalkIsOk() {
        for (int i = 0; i < 100; i++) {
            assertEquals(StuckState.OK, detector.update(sample(i * 0.2, 0, true)));
        }
    }

    @Test
    void alternatingBetweenTwoSpotsIsOscillating() {
        for (int i = 0; i < 59; i++) {
            double x = i % 2 == 0 ? 0.5 : 1.2;
            assertEquals(StuckState.OK, detector.update(sample(x, 0.5, true)), "tick " + i);
        }
        assertEquals(StuckState.OSCILLATING, detector.update(sample(1.2, 0.5, true)));
    }

    @Test
    void wanderingAcrossThreeCellsIsNotOscillating() {
        double[] spots = {0.5, 1.5, 2.5};
        for (int i = 0; i < 60; i++) {
            detector.update(sample(spots[i % 3], 0.5, true));
        }
        assertEquals(StuckState.OK, detector.update(sample(2.5, 0.5, true)));
    }

    @Test
    void spinningInPlaceIsSpinning() {
        for (int i = 0; i < 39; i++) {
            detector.update(sample(3.5, 3.5, (float) (i * 30), true));
        }
        assertEquals(StuckState.SPINNING, detector.update(sample(3.5, 3.5, 39 * 30f, true)));
    }

    @Test
    void edgeJamAfterFifteenCollisionTicks() {
        for (int i = 0; i < 14; i++) {
            assertEquals(StuckState.OK, detectorWithCollision(true));
        }
        assertEquals(StuckState.EDGE_JAM, detectorWithCollision(true));
    }

    @Test
    void edgeJamRunResetsWhenCollisionClears() {
        for (int i = 0; i < 10; i++) {
            detectorWithCollision(true);
        }
        detectorWithCollision(false);
        for (int i = 0; i < 10; i++) {
            assertEquals(StuckState.OK, detectorWithCollision(true));
        }
    }

    @Test
    void collisionWithoutIntentIsNotEdgeJam() {
        for (int i = 0; i < 30; i++) {
            assertEquals(StuckState.OK, detector.update(new MotionSample(tick++, 0, 64, 0, 0f, 0f, true, false, false,
                    true, false, MotionSample.FULL_AIR)));
        }
    }

    private StuckState detectorWithCollision(boolean collision) {
        return detector.update(new MotionSample(tick++, 2.5, 64, 2.5, 0f, 0f, true, false, false,
                collision, true, MotionSample.FULL_AIR));
    }

    @Test
    void driftingInWaterIsWaterDrift() {
        for (int i = 0; i < 59; i++) {
            assertEquals(StuckState.OK, detector.update(waterSample(i * 0.01)), "tick " + i);
        }
        assertEquals(StuckState.WATER_DRIFT, detector.update(waterSample(59 * 0.01)));
    }

    private MotionSample waterSample(double x) {
        return new MotionSample(tick++, x, 64, 0.5, 0f, 0f, false, true, false, false, true, MotionSample.FULL_AIR);
    }

    @Test
    void lowAirUnderwaterIsLowAir() {
        assertEquals(StuckState.LOW_AIR, detector.update(underwater(79, false)));
    }

    @Test
    void airAtThresholdIsNotLowAir() {
        assertEquals(StuckState.OK, detector.update(underwater(80, false)));
    }

    @Test
    void lowAirBeatsWaterDriftAndStationary() {
        for (int i = 0; i < 59; i++) {
            detector.update(underwater(MotionSample.FULL_AIR, true));
        }
        assertEquals(StuckState.LOW_AIR, detector.update(underwater(10, true)));
    }

    private MotionSample underwater(int air, boolean wantsToMove) {
        return new MotionSample(tick++, 8.5, 60, 8.5, 0f, 0f, false, true, true, false, wantsToMove, air);
    }

    @Test
    void resetClearsTheWindow() {
        for (int i = 0; i < 40; i++) {
            detector.update(sample(1.5, 1.5, true));
        }
        detector.reset();
        assertEquals(StuckState.OK, detector.update(sample(1.5, 1.5, true)));
    }
}
