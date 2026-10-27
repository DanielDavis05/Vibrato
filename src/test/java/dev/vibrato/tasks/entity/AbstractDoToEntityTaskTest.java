package dev.vibrato.tasks.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AbstractDoToEntityTaskTest {
    @Test void disabledGuardRangeDoesNotLowerDefenseNextToCow() {
        assertFalse(AbstractDoToEntityTask.shouldLowerCombatGuard(0.25, -1));
        assertTrue(AbstractDoToEntityTask.shouldLowerCombatGuard(0.25, 2));
        assertFalse(AbstractDoToEntityTask.shouldLowerCombatGuard(4, 2));
    }
}
