package dev.vibrato.fleet;

import org.junit.jupiter.api.Test;

import static dev.vibrato.fleet.AutostartGate.Decision.START;
import static dev.vibrato.fleet.AutostartGate.Decision.WAIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutostartGateTest {

    private static AutostartGate.Decision decide(boolean inGame, boolean autostart, long ticks, long delay,
                                                 boolean started, boolean busy) {
        return AutostartGate.decideStart(inGame, autostart, ticks, delay, started, busy);
    }

    @Test
    void startsOnceTheDelayHasElapsed() {
        assertEquals(WAIT, decide(true, true, 99, 100, false, false));
        assertEquals(START, decide(true, true, 100, 100, false, false));
        assertEquals(START, decide(true, true, 500, 100, false, false));
    }

    @Test
    void waitsOutsideAWorld() {
        assertEquals(WAIT, decide(false, true, 1000, 100, false, false));
    }

    @Test
    void waitsWhenAutostartIsOff() {
        assertEquals(WAIT, decide(true, false, 1000, 100, false, false));
    }

    @Test
    void neverStartsTwiceInOneSession() {
        assertEquals(WAIT, decide(true, true, 1000, 100, true, false));
    }

    @Test
    void doesNotOverrideAUserTask() {
        assertEquals(WAIT, decide(true, true, 1000, 100, false, true));
    }

    @Test
    void connectsOnceFromTheTitleScreen() {
        assertTrue(AutostartGate.shouldConnect(true, true, true, false, 1_000, AutostartGate.NEVER));
    }

    @Test
    void connectRespectsTheBackoff() {
        long first = 1_000_000;
        assertFalse(AutostartGate.shouldConnect(true, true, true, false, first + 29_999, first));
        assertTrue(AutostartGate.shouldConnect(true, true, true, false, first + 30_000, first));
    }

    @Test
    void connectOnlyFromTheTitleScreenWithAutostartAndServer() {
        assertFalse(AutostartGate.shouldConnect(false, true, true, false, 1_000, AutostartGate.NEVER));
        assertFalse(AutostartGate.shouldConnect(true, false, true, false, 1_000, AutostartGate.NEVER));
        assertFalse(AutostartGate.shouldConnect(true, true, false, false, 1_000, AutostartGate.NEVER));
    }

    @Test
    void doesNotReconnectAfterJoiningOnce() {
        assertFalse(AutostartGate.shouldConnect(true, true, true, true, 1_000_000, AutostartGate.NEVER));
    }
}
