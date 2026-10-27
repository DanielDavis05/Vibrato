package dev.vibrato.tasks.movement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpportunisticPickupPolicyTest {
    @Test void keepsNearbyDropsButAvoidsDeepOrLongDetours() {
        assertFalse(OpportunisticPickupPolicy.eligible(-13, -36, 55, -1));
        assertTrue(OpportunisticPickupPolicy.eligible(8, -2, 4, -1));
        assertTrue(OpportunisticPickupPolicy.eligible(20, 1, 0, -1));
        assertFalse(OpportunisticPickupPolicy.eligible(25, 0, 0, -1));
        assertFalse(OpportunisticPickupPolicy.eligible(2, -7, 0, -1));
        assertFalse(OpportunisticPickupPolicy.eligible(11, 0, 0, 10));
        assertTrue(OpportunisticPickupPolicy.eligible(6, 0, 0, 10));
    }
}
