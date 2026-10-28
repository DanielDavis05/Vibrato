package dev.vibrato.tasks.container;

import dev.vibrato.tasks.resources.CollectFuelTask;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmeltFuelPlanTest {
    @Test void craftingGridWoodCannotRepeatedlyPreemptCoalAcquisition() {
        var plan = new SmeltFuelPlan();
        assertEquals(0, plan.collectSmelts(1, 1.5, 0)); // One plank currently looks sufficient.
        assertEquals(1, plan.collectSmelts(1, 0, 0)); // Crafting consumes it: choose coal.
        for (int cycle = 0; cycle < 100; cycle++) {
            assertEquals(1, plan.collectSmelts(1, 1.5, 0)); // Grid cleanup returns the plank.
            assertEquals(1, plan.collectSmelts(1, 0, 0)); // Tool preparation puts it back.
        }
        assertEquals(0, plan.collectSmelts(1, 8, 1)); // Only actual acquired coal releases commitment.
    }
    @Test void coalRequestAndCompletionUseSmeltEnergyRatherThanItemCount() {
        assertEquals(1, CollectFuelTask.requiredCoal(1));
        assertEquals(1, CollectFuelTask.requiredCoal(7));
        assertEquals(1, CollectFuelTask.requiredCoal(8));
        assertEquals(2, CollectFuelTask.requiredCoal(8.01));
        var plan = new SmeltFuelPlan();
        assertEquals(9, plan.collectSmelts(9, 8, 1));
        assertEquals(9, plan.collectSmelts(9, 12, 1));
        assertEquals(0, plan.collectSmelts(9, 16, 2));
    }
    @Test void sufficientBurningFuelCanEndACommittedAcquisition() {
        var plan = new SmeltFuelPlan();
        assertEquals(1, plan.collectSmelts(1, 0, 0));
        assertEquals(0, plan.collectSmelts(0, 0, 0));
        assertEquals(0, CollectFuelTask.requiredCoal(0));
    }
}
