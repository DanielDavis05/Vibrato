package dev.vibrato.tasks.resources;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MiningApproachWatchdogTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void pacingAndSameTrunkRetargetingDoNotRenewTheDeadline() {
        MiningApproachWatchdog<Integer> watchdog = new MiningApproachWatchdog<>();
        assertFalse(watchdog.stalled(63, 0, (a, b) -> true, anchor -> 50));
        assertFalse(watchdog.stalled(64, 20 * SECOND, (a, b) -> true, anchor -> 49.5));
        assertTrue(watchdog.stalled(68, 45 * SECOND, (a, b) -> true, anchor -> 50));
    }

    @Test
    void cumulativeProgressAndCompletedExcavationRenewDeadline() {
        MiningApproachWatchdog<Integer> watchdog = new MiningApproachWatchdog<>();
        assertFalse(watchdog.stalled(1, 0, Integer::equals, anchor -> 10));
        assertFalse(watchdog.stalled(1, 30 * SECOND, Integer::equals, anchor -> 9.5));
        assertFalse(watchdog.stalled(1, 40 * SECOND, Integer::equals, anchor -> 9));
        watchdog.excavationCompleted(80 * SECOND);
        assertFalse(watchdog.stalled(1, 100 * SECOND, Integer::equals, anchor -> 9));
        assertTrue(watchdog.stalled(1, 125 * SECOND, Integer::equals, anchor -> 9));
    }

    @Test
    void unrelatedOreTargetStartsIndependentDeadline() {
        MiningApproachWatchdog<Integer> watchdog = new MiningApproachWatchdog<>();
        assertFalse(watchdog.stalled(1, 0, Integer::equals, anchor -> 10));
        assertFalse(watchdog.stalled(2, 45 * SECOND, Integer::equals, anchor -> 10));
        assertTrue(watchdog.stalled(2, 90 * SECOND, Integer::equals, anchor -> 10));
    }
}
