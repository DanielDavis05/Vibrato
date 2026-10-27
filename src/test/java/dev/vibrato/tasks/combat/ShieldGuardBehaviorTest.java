package dev.vibrato.tasks.combat;

import dev.vibrato.tasks.combat.ShieldGuardBehavior.Threat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShieldGuardBehaviorTest {

    @Test
    void creeperBlastOutranksChargeOutranksArrow() {
        assertEquals(Threat.BLAST, ShieldGuardBehavior.classify(true, true, true));
        assertEquals(Threat.CHARGE, ShieldGuardBehavior.classify(true, false, true));
        assertEquals(Threat.ARROW_LINE, ShieldGuardBehavior.classify(true, false, false));
        assertEquals(Threat.NONE, ShieldGuardBehavior.classify(false, false, false));
    }

    @Test
    void raisesOnlyWhenThreatenedWithAShield() {
        assertTrue(ShieldGuardBehavior.shouldRaise(true, true, false, false));
        assertFalse(ShieldGuardBehavior.shouldRaise(false, true, false, false));
        assertFalse(ShieldGuardBehavior.shouldRaise(true, false, false, false));
    }

    @Test
    void eatingAndShootingTakePrecedence() {
        assertFalse(ShieldGuardBehavior.shouldRaise(true, true, true, false));
        assertFalse(ShieldGuardBehavior.shouldRaise(true, true, false, true));
    }
}
