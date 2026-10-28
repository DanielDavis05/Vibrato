package dev.vibrato.tasks.resources;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodPursuitProgressTest {
    @Test void stationaryObstructedTargetEventuallyExpires() {
        var lease = new FoodPursuitProgress();
        assertFalse(lease.stalled(0, 4, 10));
        assertFalse(lease.stalled(599, 4, 10));
        assertTrue(lease.stalled(600, 4, 10));
    }

    @Test void approachAndDamageExtendLeaseButLookingDoesNot() {
        var lease = new FoodPursuitProgress();
        assertFalse(lease.stalled(0, 100, 10));
        assertFalse(lease.stalled(500, 64, 10));
        assertFalse(lease.stalled(1000, 64, 9));
        assertTrue(lease.stalled(1600, 64, 9));
    }

    @Test void periodicRescanRetainsLiveActivePursuit() {
        assertTrue(FoodPursuitProgress.retainOnRescan(true, true));
        assertFalse(FoodPursuitProgress.retainOnRescan(false, true));
        assertFalse(FoodPursuitProgress.retainOnRescan(true, false));
    }

    @Test void interruptionStartsFreshLease() {
        var lease = new FoodPursuitProgress();
        lease.stalled(0, 4, 10);
        lease.reset();
        assertFalse(lease.stalled(2000, 4, 10));
    }
}
