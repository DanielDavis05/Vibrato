package dev.vibrato.director;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepHealthTest {

    @Test
    void neverStuckBeforeMinimumRuntime() {
        StepHealth health = new StepHealth(100, 10);
        for (int tick = 0; tick < 99; tick++) {
            health.tick(true);
        }
        assertFalse(health.verdict());
    }

    @Test
    void stuckVerdictNeedsPersistence() {
        StepHealth health = new StepHealth(100, 10);
        for (int tick = 0; tick < 100; tick++) {
            health.tick(tick % 5 == 0); // stuck signal flickers and never lasts 10 ticks
        }
        assertFalse(health.verdict());
    }

    @Test
    void stuckVerdictAfterRuntimeAndPersistence() {
        StepHealth health = new StepHealth(100, 10);
        for (int tick = 0; tick < 50; tick++) {
            health.tick(false);
        }
        for (int tick = 0; tick < 50; tick++) {
            health.tick(true);
        }
        assertTrue(health.verdict());
    }

    @Test
    void defaultsRequireTwentySecondsBeforeAnyVerdict() {
        StepHealth health = new StepHealth();
        for (int tick = 0; tick < 19 * 20; tick++) {
            health.tick(true);
        }
        assertFalse(health.verdict());
        for (int tick = 0; tick < 12 * 20; tick++) {
            health.tick(true);
        }
        assertTrue(health.verdict());
    }
    @Test
    void movingWanderKeepsMakingProgress() {
        StepHealth health = new StepHealth(100, 30);
        for (int tick = 0; tick < 1000; tick++) {
            health.tick(true, tick * 0.05, 64, 0);
        }
        assertFalse(health.verdict());
    }

    @Test
    void stationaryWanderEventuallyFailsAndMovementResetsTheClock() {
        StepHealth health = new StepHealth(10, 5);
        for (int tick = 0; tick < 10; tick++) health.tick(true, 0, 64, 0);
        assertTrue(health.verdict());
        health.tick(true, 1, 64, 0);
        assertFalse(health.verdict());
        for (int tick = 0; tick < 5; tick++) health.tick(true, 1, 64, 0);
        assertTrue(health.verdict());
    }
}
