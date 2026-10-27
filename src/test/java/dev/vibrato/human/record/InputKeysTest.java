package dev.vibrato.human.record;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputKeysTest {

    @Test
    void idleHasNoKeys() {
        assertEquals(new InputKeys.Pressed(false, false, false, false, false, false),
                InputKeys.fromAxes(0f, 0f, false, false));
    }

    @Test
    void forwardAxisIsForwardKey() {
        InputKeys.Pressed keys = InputKeys.fromAxes(1f, 0f, false, false);
        assertTrue(keys.forward());
        assertFalse(keys.back());
    }

    @Test
    void negativeForwardAxisIsBackKey() {
        InputKeys.Pressed keys = InputKeys.fromAxes(-1f, 0f, false, false);
        assertFalse(keys.forward());
        assertTrue(keys.back());
    }

    @Test
    void positiveSidewaysIsLeftKey() {
        InputKeys.Pressed keys = InputKeys.fromAxes(0f, 1f, false, false);
        assertTrue(keys.left());
        assertFalse(keys.right());
    }

    @Test
    void negativeSidewaysIsRightKey() {
        InputKeys.Pressed keys = InputKeys.fromAxes(0f, -1f, false, false);
        assertFalse(keys.left());
        assertTrue(keys.right());
    }

    @Test
    void axesWithinDeadzoneAreReleased() {
        InputKeys.Pressed keys = InputKeys.fromAxes(InputKeys.DEADZONE, -InputKeys.DEADZONE, false, false);
        assertFalse(keys.forward());
        assertFalse(keys.back());
        assertFalse(keys.left());
        assertFalse(keys.right());
    }

    @Test
    void scaledSneakAxesStillCount() {
        // Sneaking scales movement to 0.3 in vanilla; that must still register as a held key.
        InputKeys.Pressed keys = InputKeys.fromAxes(0.3f, -0.3f, false, true);
        assertTrue(keys.forward());
        assertTrue(keys.right());
        assertTrue(keys.sneak());
    }

    @Test
    void jumpAndSneakPassThrough() {
        InputKeys.Pressed keys = InputKeys.fromAxes(0f, 0f, true, true);
        assertTrue(keys.jump());
        assertTrue(keys.sneak());
        assertFalse(keys.forward());
    }

    @Test
    void diagonalSetsTwoAxes() {
        InputKeys.Pressed keys = InputKeys.fromAxes(1f, 1f, false, false);
        assertTrue(keys.forward());
        assertTrue(keys.left());
        assertFalse(keys.back());
        assertFalse(keys.right());
    }
}
