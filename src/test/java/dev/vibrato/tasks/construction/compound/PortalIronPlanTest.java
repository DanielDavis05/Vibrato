package dev.vibrato.tasks.construction.compound;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PortalIronPlanTest {
    @Test void batchesMissingBucketsAndIgniter() {
        assertEquals(7, PortalIronPlan.ingotGoal(0, false));
        assertEquals(4, PortalIronPlan.ingotGoal(1, false));
        assertEquals(6, PortalIronPlan.ingotGoal(0, true));
        assertEquals(3, PortalIronPlan.ingotGoal(1, true));
        assertEquals(0, PortalIronPlan.ingotGoal(2, false));
        assertEquals(0, PortalIronPlan.ingotGoal(2, true));
        assertEquals(0, PortalIronPlan.ingotGoal(3, false));
    }
}
