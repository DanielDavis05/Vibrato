package dev.vibrato.tasks.construction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static dev.vibrato.tasks.construction.PlacementMaterialPolicy.Action.*;

class PlacementMaterialPolicyTest {
    @Test void craftOutputOnCursorIsMovedBeforeFailingOrBuilding() {
        assertEquals(MOVE_CURSOR, PlacementMaterialPolicy.decide(false, true, 0));
        assertEquals(MOVE_CURSOR, PlacementMaterialPolicy.decide(false, true, 20));
        assertEquals(READY, PlacementMaterialPolicy.decide(true, true, 0));
    }

    @Test void closedScreenHandoffHasBoundedWaitThenFails() {
        assertEquals(WAIT, PlacementMaterialPolicy.decide(false, false, 20));
        assertEquals(WAIT, PlacementMaterialPolicy.decide(false, false, 1));
        assertEquals(FAIL, PlacementMaterialPolicy.decide(false, false, 0));
        assertEquals(READY, PlacementMaterialPolicy.decide(true, false, 10));
    }
}
