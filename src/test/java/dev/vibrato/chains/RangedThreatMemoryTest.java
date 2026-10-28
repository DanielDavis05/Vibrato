package dev.vibrato.chains;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RangedThreatMemoryTest {
    @Test void healthyIntentionalCombatIsAllowedButVulnerableCombatStillRetreats() {
        assertFalse(RangedThreatMemory.shouldDefend(true, false));
        assertTrue(RangedThreatMemory.shouldDefend(true, true));
        assertTrue(RangedThreatMemory.shouldDefend(false, false));
    }
    @Test void onlyActiveVisibleNearbyAttackersAreAcquired() {
        assertTrue(RangedThreatMemory.observe(true, true, false, 20 * 20, true));
        assertFalse(RangedThreatMemory.observe(true, false, false, 20 * 20, true));
        assertFalse(RangedThreatMemory.observe(true, true, false, 25 * 25, true));
        assertFalse(RangedThreatMemory.observe(true, true, false, 20 * 20, false));
        assertFalse(RangedThreatMemory.observe(false, true, false, 20 * 20, true));
    }
    @Test void briefLossOfSightKeepsRetreatButMemoryExpiresOrClears() {
        assertTrue(RangedThreatMemory.retain(true, false, 27 * 27, 250, 100));
        assertFalse(RangedThreatMemory.retain(true, false, 27 * 27, 300, 100));
        assertFalse(RangedThreatMemory.retain(true, false, 30 * 30, 101, 100));
        assertFalse(RangedThreatMemory.retain(false, false, 20 * 20, 101, 100));
        assertFalse(RangedThreatMemory.retain(true, true, 20 * 20, 101, 100));
        assertFalse(RangedThreatMemory.retain(true, false, 20 * 20, 99, 100));
    }
}
