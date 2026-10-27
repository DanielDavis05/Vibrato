package dev.vibrato.tasks.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClutchItemPolicyTest {
    @Test
    void waterRequiresActualSelectedMainHandAndPermittedDimension() {
        assertTrue(ClutchItemPolicy.mayUse(true, false, true));
        assertFalse(ClutchItemPolicy.mayUse(true, false, false)); // inventory/offhand or swap pending
        assertFalse(ClutchItemPolicy.mayUse(true, true, true));
        assertFalse(ClutchItemPolicy.mayUse(true, true, false));
    }

    @Test
    void nonWaterClutchRemainsAvailableInUltrawarmDimensions() {
        assertTrue(ClutchItemPolicy.mayUse(false, true, true));
        assertFalse(ClutchItemPolicy.mayUse(false, true, false));
    }
}
