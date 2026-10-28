package dev.vibrato.tasks.trade;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VillagerRetryTrackerTest {
    @Test void rejectionAllowsAnotherVillagerAndExpiresForStateChanges() {
        VillagerRetryTracker tracker = new VillagerRetryTracker(120);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        tracker.reject(first, 10);
        assertFalse(tracker.available(first, 129));
        assertTrue(tracker.available(second, 129));
        assertTrue(tracker.available(first, 130));
    }
}
