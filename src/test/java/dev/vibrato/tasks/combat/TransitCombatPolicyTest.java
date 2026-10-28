package dev.vibrato.tasks.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransitCombatPolicyTest {
    @Test void passiveInteractionDoesNotPreventEscapeFromHostiles() {
        assertTrue(TransitCombatPolicy.shouldEscapeDanger(false, false, false));
        assertFalse(TransitCombatPolicy.shouldEscapeDanger(true, false, false));
        assertTrue(TransitCombatPolicy.shouldEscapeDanger(true, true, false)); // vulnerable transit combat must yield
        assertTrue(TransitCombatPolicy.shouldEscapeDanger(true, false, true)); // explicit combat yields at critical health
        assertFalse(TransitCombatPolicy.mayStart(199, 100)); // escape cooldown prevents immediate transit re-engagement
    }

    @Test void criticalHealthAlwaysOverridesArmorAndNearbyHostilesOverrideEating() {
        assertTrue(TransitCombatPolicy.vulnerable(20, 2));
        assertTrue(TransitCombatPolicy.vulnerable(20, 6));
        assertFalse(TransitCombatPolicy.vulnerable(20, 7));
        assertFalse(TransitCombatPolicy.yieldToEating(true, true, true));
        assertTrue(TransitCombatPolicy.yieldToEating(true, true, false));
        assertTrue(TransitCombatPolicy.yieldToEating(true, false, true));
    }

    @Test void visibleAngryRangedThreatWithinTwelveBlocksRequiresEscape() {
        assertTrue(TransitCombatPolicy.rangedThreat(true, true, false, 144, true));
        assertFalse(TransitCombatPolicy.rangedThreat(true, true, false, 144.1, true));
        assertFalse(TransitCombatPolicy.rangedThreat(true, true, false, 25, false));
        assertFalse(TransitCombatPolicy.rangedThreat(true, false, false, 25, true));
        assertFalse(TransitCombatPolicy.rangedThreat(false, true, false, 25, true));
        assertFalse(TransitCombatPolicy.rangedThreat(true, true, true, 25, true));
    }

    @Test void requiresNearbyVisibleLiveTarget() {
        assertTrue(TransitCombatPolicy.eligible(true, 25, true));
        assertFalse(TransitCombatPolicy.eligible(true, 25.1, true));
        assertFalse(TransitCombatPolicy.eligible(true, 2, false));
        assertFalse(TransitCombatPolicy.eligible(false, 2, true));
    }

    @Test void commitmentAllowsSomeMovementButEndsAndHasCooldown() {
        assertTrue(TransitCombatPolicy.keepEngaging(true, 64, 119));
        assertFalse(TransitCombatPolicy.keepEngaging(true, 64, 120));
        assertFalse(TransitCombatPolicy.keepEngaging(true, 65, 1));
        assertFalse(TransitCombatPolicy.keepEngaging(false, 1, 1));
        assertFalse(TransitCombatPolicy.mayStart(199, 100));
        assertTrue(TransitCombatPolicy.mayStart(200, 100));
        assertTrue(TransitCombatPolicy.mayStart(1, 100));
    }

    @Test void criticalJumpWaitsForDescendingPhaseButDoesNotHang() {
        assertTrue(CriticalHitPolicy.waitForFall(0, true, 0));
        assertTrue(CriticalHitPolicy.waitForFall(5, false, 0));
        assertFalse(CriticalHitPolicy.waitForFall(8, false, 0.1f));
        assertFalse(CriticalHitPolicy.waitForFall(12, true, 0));
        assertFalse(CriticalHitPolicy.waitForFall(-1, true, 0));
    }
}
