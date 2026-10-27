package dev.vibrato.tasks.resources;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShearWoolPolicyTest {
    @Test void requiresAnAdultLivingShearableSheep() {
        assertTrue(ShearWoolPolicy.ready(true, false, true, false));
        assertFalse(ShearWoolPolicy.ready(false, false, true, false));
        assertFalse(ShearWoolPolicy.ready(true, true, true, false));
        assertFalse(ShearWoolPolicy.ready(true, false, false, false));
        assertFalse(ShearWoolPolicy.ready(true, false, true, true));
    }

    @Test void completionRequiresShearingAndNewWoolEvenWhenAlreadyStocked() {
        assertEquals(11, ShearWoolPolicy.targetCount(3, 10));
        assertEquals(6, ShearWoolPolicy.targetCount(6, 1));
        assertFalse(ShearWoolPolicy.finished(false, 11, 11));
        assertFalse(ShearWoolPolicy.finished(true, 10, 11));
        assertTrue(ShearWoolPolicy.finished(true, 11, 11));
    }

    @Test void interruptionKeepsTheOriginalGoalAfterWoolIsCollected() {
        int goal = ShearWoolPolicy.resumeTarget(0, 3, 5);
        assertEquals(6, goal);
        assertEquals(goal, ShearWoolPolicy.resumeTarget(goal, 3, 6));
        assertTrue(ShearWoolPolicy.finished(true, 6, goal));
    }
}
