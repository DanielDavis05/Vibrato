package dev.vibrato.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscapeTriggerTest {

    @Test
    void neverEscapesFromTheSurface() {
        assertFalse(EscapeTrigger.shouldEscape(false, false, 100_000));
    }

    @Test
    void escapesUndergroundWithoutAPickaxeAtOnce() {
        assertTrue(EscapeTrigger.shouldEscape(true, false, 0));
    }

    @Test
    void undergroundWithAPickaxeEscapesOnlyAfterAStall() {
        assertFalse(EscapeTrigger.shouldEscape(true, true, EscapeTrigger.STALL_TICKS - 1));
        assertTrue(EscapeTrigger.shouldEscape(true, true, EscapeTrigger.STALL_TICKS));
    }
}
