package dev.vibrato.tasks.speedrun;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GamerStageRecoveryTest {
    private static final String HANDOFF = "PlaceBlockTask: No usable block to place at 19, 55, 15 (requested [furnace])";

    @Test void retriesAreBoundedPerStageAndResetOnlyOnCompletion() {
        var recovery = new GamerStageRecovery();
        assertTrue(recovery.retry(GamerStageRecovery.Stage.FOOD, HANDOFF));
        assertTrue(recovery.retry(GamerStageRecovery.Stage.FOOD, HANDOFF));
        assertFalse(recovery.retry(GamerStageRecovery.Stage.FOOD, HANDOFF));
        recovery.completed(GamerStageRecovery.Stage.FOOD);
        assertTrue(recovery.retry(GamerStageRecovery.Stage.FOOD, HANDOFF));
    }

    @Test void unrelatedAndPersistentFailuresPropagate() {
        var recovery = new GamerStageRecovery();
        assertFalse(recovery.retry(GamerStageRecovery.Stage.FOOD, null));
        assertFalse(recovery.retry(GamerStageRecovery.Stage.FOOD, "PlaceBlockTask: No inventory space for requested placement material"));
        assertFalse(recovery.retry(GamerStageRecovery.Stage.FOOD, "PlaceBlockTask: Cannot collect a usable structure block"));
        assertFalse(recovery.retry(GamerStageRecovery.Stage.FOOD, "OtherTask: No usable block to place at 1, 2, 3"));
        assertTrue(recovery.retry(GamerStageRecovery.Stage.FOOD, HANDOFF));
    }
}
