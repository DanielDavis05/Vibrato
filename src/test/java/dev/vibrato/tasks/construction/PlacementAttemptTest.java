package dev.vibrato.tasks.construction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlacementAttemptTest {
    @Test void lastItemWaitsOnlyAfterAnInteractionAndEventuallyFailsIfUnconfirmed() {
        var attempt = new PlacementAttempt();
        assertFalse(attempt.waitForResult(false));
        attempt.interacted();
        for (int i = 0; i < 40; i++) assertTrue(attempt.waitForResult(false));
        assertFalse(attempt.waitForResult(false));
        attempt.interacted(); // repeated clicks cannot extend a lost result forever
        assertFalse(attempt.waitForResult(false));
    }

    @Test void restoredMaterialEndsPendingWaitAndAllowsAnotherAttempt() {
        var attempt = new PlacementAttempt();
        attempt.interacted();
        assertTrue(attempt.waitForResult(false));
        assertFalse(attempt.waitForResult(true));
        assertFalse(attempt.waitForResult(false));
        attempt.interacted();
        assertTrue(attempt.waitForResult(false));
    }
}
