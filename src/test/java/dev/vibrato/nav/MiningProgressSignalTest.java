package dev.vibrato.nav;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MiningProgressSignalTest {
    @Test
    void eventProgressCountsWithoutVanillaBreakingFlag() {
        MiningProgressSignal signal = new MiningProgressSignal();
        assertTrue(signal.observe(12L, 0.1, false, false));
        assertTrue(signal.observe(12L, 0.3, false, false));
        assertTrue(signal.observe(12L, 0.7, false, false));
        assertTrue(signal.observe(13L, 0, false, false));
    }

    @Test
    void staleEventCannotRenewGrace() {
        MiningProgressSignal signal = new MiningProgressSignal();
        long lastProgressTick = 0;
        assertTrue(signal.observe(12L, 0.5, false, false));
        for (long tick = 1; tick <= NavGate.BREAK_GRACE_TICKS + 1; tick++) {
            if (signal.observe(12L, 0.5, false, false)) lastProgressTick = tick;
        }
        assertFalse(NavGate.withinBreakGrace(NavGate.BREAK_GRACE_TICKS + 1, lastProgressTick));
    }

    @Test
    void finishedBlockCountsOnceEvenIfEventIsStaleOrDisappears() {
        MiningProgressSignal signal = new MiningProgressSignal();
        assertTrue(signal.observe(12L, 0.9, false, false));
        assertTrue(signal.observe(12L, 0.9, true, true));
        assertFalse(signal.observe(12L, 0.9, true, false));
        assertFalse(signal.observe(null, 0, false, false));
    }
}
