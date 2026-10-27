package dev.vibrato.tasks.organize;

import dev.vibrato.tasks.organize.InventoryPlan.Plan;
import dev.vibrato.tasks.organize.InventoryPlan.Stack;
import dev.vibrato.tasks.organize.InventoryPlan.Swap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InventoryPlanTest {

    /** Applies the swaps to a slot-to-id array and returns the resulting contents. */
    private static String[] apply(List<Stack> inventory, Plan plan) {
        String[] slots = new String[InventoryPlan.MAIN_SIZE];
        for (Stack stack : inventory) {
            slots[stack.slot()] = stack.id();
        }
        for (Swap swap : plan.swaps()) {
            String moved = slots[swap.from()];
            slots[swap.from()] = slots[swap.hotbar()];
            slots[swap.hotbar()] = moved;
        }
        return slots;
    }

    @Test
    void classifiesByItemId() {
        assertEquals(InventoryPlan.Role.SWORD, InventoryPlan.classify("diamond_sword"));
        assertEquals(InventoryPlan.Role.AXE, InventoryPlan.classify("iron_axe"));
        assertEquals(InventoryPlan.Role.PICKAXE, InventoryPlan.classify("iron_pickaxe"));
        assertEquals(InventoryPlan.Role.FOOD, InventoryPlan.classify("cooked_beef"));
        assertEquals(InventoryPlan.Role.JUNK, InventoryPlan.classify("rotten_flesh"));
        assertEquals(InventoryPlan.Role.DIRT_LIKE, InventoryPlan.classify("dirt"));
        assertEquals(InventoryPlan.Role.BLOCK, InventoryPlan.classify("oak_planks"));
        assertEquals(InventoryPlan.Role.VALUABLE, InventoryPlan.classify("emerald"));
        assertEquals(InventoryPlan.Role.OTHER, InventoryPlan.classify("string"));
    }

    @Test
    void tiersOrderMaterials() {
        assertTrue(InventoryPlan.tier("netherite_pickaxe") > InventoryPlan.tier("diamond_pickaxe"));
        assertTrue(InventoryPlan.tier("iron_sword") > InventoryPlan.tier("stone_sword"));
        assertTrue(InventoryPlan.tier("stone_sword") > InventoryPlan.tier("wooden_sword"));
        assertEquals(-1, InventoryPlan.tier("torch"));
    }

    @Test
    void bestSwordAndPickaxeGoToFirstHotbarSlots() {
        List<Stack> inv = List.of(
                new Stack(0, "stone_sword", 1),
                new Stack(12, "diamond_sword", 1),
                new Stack(20, "wooden_pickaxe", 1),
                new Stack(21, "iron_pickaxe", 1));
        String[] after = apply(inv, InventoryPlan.plan(inv));
        assertEquals("diamond_sword", after[0]);
        assertEquals("iron_pickaxe", after[1]);
    }

    @Test
    void foodStackAndTorchesLandOnTheirSlots() {
        List<Stack> inv = List.of(
                new Stack(9, "bread", 3),
                new Stack(10, "cooked_beef", 12),
                new Stack(11, "torch", 16));
        String[] after = apply(inv, InventoryPlan.plan(inv));
        assertEquals("cooked_beef", after[4]);
        assertEquals("torch", after[5]);
    }

    @Test
    void blocksGoToHotbarTailLargestFirst() {
        List<Stack> inv = List.of(
                new Stack(9, "oak_planks", 20),
                new Stack(10, "stone", 64),
                new Stack(11, "spruce_planks", 5));
        String[] after = apply(inv, InventoryPlan.plan(inv));
        assertEquals("stone", after[7]);
        assertEquals("oak_planks", after[8]);
    }

    @Test
    void junkAndSurplusDirtAreDumped() {
        List<Stack> inv = List.of(
                new Stack(9, "rotten_flesh", 4),
                new Stack(10, "dirt", 64),
                new Stack(11, "dirt", 64),
                new Stack(12, "dirt", 64),
                new Stack(13, "poisonous_potato", 1),
                new Stack(14, "emerald", 2));
        Plan plan = InventoryPlan.plan(inv);
        assertTrue(plan.dump().containsAll(List.of(9, 13)));
        // two dirt stacks ride the hotbar tail; only the surplus third stack is thrown out
        assertEquals(1, plan.dump().stream().filter(slot -> slot >= 10 && slot <= 12).count());
        assertFalse(plan.dump().contains(14));
    }

    @Test
    void twoStacksOfDirtAreKept() {
        List<Stack> inv = List.of(new Stack(9, "dirt", 64), new Stack(10, "cobblestone", 64));
        assertTrue(InventoryPlan.plan(inv).dump().isEmpty());
    }

    @Test
    void stashableIsValuableOrOtherOnly() {
        assertTrue(InventoryPlan.isStashable("diamond"));
        assertTrue(InventoryPlan.isStashable("string"));
        assertFalse(InventoryPlan.isStashable("iron_sword"));
        assertFalse(InventoryPlan.isStashable("bread"));
        assertFalse(InventoryPlan.isStashable("oak_planks"));
    }

    @Test
    void emptyInventoryNeedsNothing() {
        Plan plan = InventoryPlan.plan(new ArrayList<>());
        assertTrue(plan.swaps().isEmpty());
        assertTrue(plan.dump().isEmpty());
    }

    @Test
    void alreadyOrderedInventoryNeedsNoSwaps() {
        List<Stack> inv = List.of(new Stack(0, "iron_sword", 1), new Stack(1, "iron_pickaxe", 1));
        assertTrue(InventoryPlan.plan(inv).swaps().isEmpty());
    }
}
