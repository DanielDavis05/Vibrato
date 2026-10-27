package dev.vibrato.tasks;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeferredContainersTest {
    @Test
    void stalledContainerStaysDeferredUntilWithinReach() {
        DeferredContainers<String> deferred = new DeferredContainers<>();
        deferred.defer("furnace", 100);
        assertFalse(deferred.eligible("furnace", 100));
        assertTrue(deferred.eligible("other", 100));
        assertFalse(deferred.eligible("furnace", 16.01));
        assertTrue(deferred.eligible("furnace", 16));
        assertTrue(deferred.eligible("furnace", 100));
    }

    @Test
    void failureWhileAlreadyNearbyNeedsLeavingAndReenteringRange() {
        DeferredContainers<String> deferred = new DeferredContainers<>();
        deferred.defer("furnace", 9);
        assertFalse(deferred.eligible("furnace", 9));
        assertFalse(deferred.eligible("furnace", 20));
        assertTrue(deferred.eligible("furnace", 9));
    }

    @Test
    void taskInstancesKeepIndependentDeferrals() {
        DeferredContainers<String> first = new DeferredContainers<>();
        DeferredContainers<String> second = new DeferredContainers<>();
        first.defer("chest", 25);
        assertFalse(first.eligible("chest", 25));
        assertTrue(second.eligible("chest", 25));
    }
}
