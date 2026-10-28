package dev.vibrato.tasks.movement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.vibrato.tasks.movement.NetherExpeditionPlan.Stage.*;

class NetherExpeditionPlanTest {
    @Test void woodToolAndWeaponStagesSurviveConsumableHandoffs() {
        var plan = new NetherExpeditionPlan();
        assertEquals(WOOD, plan.update(3, 0, false));
        assertEquals(TOOLS, plan.update(4, 0, false));
        assertEquals(TOOLS, plan.update(0, 1, false)); // logs used for recipe
        assertEquals(WEAPON, plan.update(0, 2, false));
        assertEquals(WEAPON, plan.update(0, 1, false)); // weapon child retains ownership
        assertEquals(TOOLS, plan.update(0, 1, true)); // both tools must still be usable
        assertEquals(COMPLETE, plan.update(0, 2, true));
        assertEquals(COMPLETE, plan.update(0, 0, false));
    }
    @Test void wornToolRaisesGoalOnlyAfterPreviousGoalPhysicallyArrives() {
        assertEquals(2, NetherExpeditionPlan.stoneGoal(0, 0, 0));
        assertEquals(2, NetherExpeditionPlan.stoneGoal(2, 1, 1));
        assertEquals(3, NetherExpeditionPlan.stoneGoal(2, 2, 1));
        assertEquals(3, NetherExpeditionPlan.stoneGoal(3, 2, 1));
        assertEquals(0, NetherExpeditionPlan.stoneGoal(3, 3, 2));
    }
    @Test void weaponGoalWaitsForPhysicalOutputAndReplacesWornSword() {
        assertEquals(1, NetherExpeditionPlan.weaponGoal(0, 0, false));
        assertEquals(1, NetherExpeditionPlan.weaponGoal(1, 0, false)); // cursor handoff
        assertEquals(2, NetherExpeditionPlan.weaponGoal(1, 1, false)); // physical but unusable
        assertEquals(2, NetherExpeditionPlan.weaponGoal(2, 1, false));
        assertEquals(0, NetherExpeditionPlan.weaponGoal(2, 2, true));
        assertTrue(NetherWeaponPolicy.durable(100, 131, 1));
        assertFalse(NetherWeaponPolicy.durable(130, 131, 1));
    }
    @Test void strongerExistingToolsAndWeaponAvoidCrafting() {
        var plan = new NetherExpeditionPlan();
        assertEquals(COMPLETE, plan.update(4, 2, true));
        plan.reset();
        assertEquals(WOOD, plan.update(0, 2, true));
        assertEquals(COMPLETE, plan.update(4, 2, true));
    }
}
