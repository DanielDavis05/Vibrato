package dev.vibrato.tasks.movement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NetherFoodPlanTest {
    @Test void emptyPortalConstructorCommitsUntilActualFoodStockIsReady() {
        var plan = new NetherFoodPlan();
        assertTrue(plan.shouldCollect(true, 0));
        assertTrue(plan.shouldCollect(true, 5));
        assertTrue(plan.shouldCollect(true, 15));
        assertFalse(plan.shouldCollect(true, 16));
        assertFalse(plan.shouldCollect(true, 0), "later emergencies belong to bounded food safety");
    }
    @Test void existingFoodAndTripsNotConstructingAPortalDoNotStartPreflight() {
        assertFalse(new NetherFoodPlan().shouldCollect(true, 1));
        assertFalse(new NetherFoodPlan().shouldCollect(false, 0));
    }
    @Test void respawnCanPrepareAgainWithoutGrantingOrRestoringItems() {
        var plan = new NetherFoodPlan();
        assertTrue(plan.shouldCollect(true, 0));
        assertFalse(plan.shouldCollect(true, 16));
        plan.reset();
        assertTrue(plan.shouldCollect(true, 0));
    }
}
