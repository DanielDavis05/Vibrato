package dev.vibrato.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavGateTest {

    @Test
    void quietWhenNothingElseOwnsTheBot() {
        assertFalse(NavGate.suppressed(false, false, false, false, false, false));
    }

    @Test
    void quietWhenAnyOtherOwnerIsActive() {
        assertTrue(NavGate.suppressed(true, false, false, false, false, false));
        assertTrue(NavGate.suppressed(false, true, false, false, false, false));
        assertTrue(NavGate.suppressed(false, false, true, false, false, false));
        assertTrue(NavGate.suppressed(false, false, false, true, false, false));
        assertTrue(NavGate.suppressed(false, false, false, false, true, false));
        assertTrue(NavGate.suppressed(false, false, false, false, false, true));
    }
}
