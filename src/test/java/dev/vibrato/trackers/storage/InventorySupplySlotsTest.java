package dev.vibrato.trackers.storage;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class InventorySupplySlotsTest {
    @Test
    void filledCraftingInputsAreNotSupplyForTheNextIngredient() {
        // A partly filled crafting-table recipe in the 2x2 grid: three planks already placed,
        // another three still in the hotbar. Filling input 4 must use hotbar 36, not steal input 1.
        Map<Integer, Integer> stacks = Map.of(1, 1, 2, 1, 3, 1, 36, 3);
        List<Integer> sources = stacks.keySet().stream()
                .filter(InventorySubTracker::isMainPlayerInventoryWindowSlot).toList();
        assertEquals(List.of(36), sources);
        assertEquals(3, sources.stream().mapToInt(stacks::get).sum());
        assertFalse(InventorySubTracker.isMainPlayerInventoryWindowSlot(4));
    }

    @Test
    void mainInventoryAndHotbarRemainAvailableButOutputArmorAndOffhandDoNot() {
        assertTrue(InventorySubTracker.isMainPlayerInventoryWindowSlot(9));
        assertTrue(InventorySubTracker.isMainPlayerInventoryWindowSlot(35));
        assertTrue(InventorySubTracker.isMainPlayerInventoryWindowSlot(36));
        assertTrue(InventorySubTracker.isMainPlayerInventoryWindowSlot(44));
        for (int slot : new int[]{-1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 45})
            assertFalse(InventorySubTracker.isMainPlayerInventoryWindowSlot(slot));
    }
}
