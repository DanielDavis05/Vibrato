package dev.vibrato.tasks.movement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PickupApproachWatchdogTest {
    private static final long SECOND = 1_000_000_000L;

    @Test void repeatedFailedCalculationsCannotResetAStationaryApproach() {
        var watchdog = new PickupApproachWatchdog<String>();
        assertFalse(watchdog.stalled("drop", 0, 3, 0, false, false));
        for (int second = 1; second < 45; second++) {
            assertFalse(watchdog.stalled("drop", second * SECOND, 3, 0, false, false));
        }
        assertTrue(watchdog.stalled("drop", 45 * SECOND, 3, 0, false, false));
        assertFalse(watchdog.stalled("other drop", 46 * SECOND, 3, 0, false, false));
    }

    @Test void approachingAcquiringAndExcavatingEachRenewTheBudget() {
        var watchdog = new PickupApproachWatchdog<String>();
        assertFalse(watchdog.stalled("drop", 0, 5, 0, false, false));
        assertFalse(watchdog.stalled("drop", 40 * SECOND, 4, 0, false, false));
        assertFalse(watchdog.stalled("drop", 80 * SECOND, 4, 1, false, false));
        assertFalse(watchdog.stalled("drop", 120 * SECOND, 4, 1, false, true));
        assertTrue(watchdog.stalled("drop", 165 * SECOND, 4, 1, false, false));
    }

    @Test void airborneCraftingAndInterruptionsDoNotConsumeTheBudget() {
        var watchdog = new PickupApproachWatchdog<String>();
        assertFalse(watchdog.stalled("drop", 0, 3, 0, false, false));
        assertFalse(watchdog.stalled("drop", 20 * SECOND, 3, 0, false, false));
        assertFalse(watchdog.stalled("drop", 100 * SECOND, 3, 0, true, false));
        watchdog.suspend();
        assertFalse(watchdog.stalled("drop", 200 * SECOND, 3, 0, false, false));
        assertFalse(watchdog.stalled("drop", 224 * SECOND, 3, 0, false, false));
        assertTrue(watchdog.stalled("drop", 225 * SECOND, 3, 0, false, false));
    }

    @Test void minorOscillationDoesNotPretendToMakeProgress() {
        var watchdog = new PickupApproachWatchdog<String>();
        assertFalse(watchdog.stalled("drop", 0, 3, 0, false, false));
        assertFalse(watchdog.stalled("drop", 40 * SECOND, 2.8, 0, false, false));
        assertTrue(watchdog.stalled("drop", 45 * SECOND, 3, 0, false, false));
    }
}
