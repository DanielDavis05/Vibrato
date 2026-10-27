package dev.vibrato.director;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterruptTrackerTest {

    @Test
    void sixInterruptsWithoutProgressIsHarassment() {
        InterruptTracker tracker = new InterruptTracker(1200, 6);
        for (int i = 0; i < 5; i++) {
            tracker.interrupted(i * 100, 7L);
        }
        assertFalse(tracker.harassed(500));
        tracker.interrupted(500, 7L);
        assertTrue(tracker.harassed(500));
    }

    @Test
    void progressResetsTheCount() {
        InterruptTracker tracker = new InterruptTracker(1200, 6);
        for (int i = 0; i < 5; i++) {
            tracker.interrupted(i * 100, 7L);
        }
        tracker.interrupted(500, 8L); // the bot moved or its inventory changed
        assertFalse(tracker.harassed(500));
    }

    @Test
    void oldInterruptsFallOutOfTheWindow() {
        InterruptTracker tracker = new InterruptTracker(1200, 6);
        for (int i = 0; i < 6; i++) {
            tracker.interrupted(i * 100, 7L);
        }
        assertTrue(tracker.harassed(500));
        assertFalse(tracker.harassed(1301));
    }
}
