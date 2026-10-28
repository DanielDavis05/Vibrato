package dev.vibrato.tasks.slot;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.tasksystem.TaskChain;
import dev.vibrato.tasksystem.TaskRunner;
import dev.vibrato.util.slots.PlayerSlot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftingOutputHandoffTest {
    private static final class Chain extends TaskChain {
        Chain() { super(new TaskRunner(null)); }
        @Override protected void onStop(Vibrato mod) {}
        @Override public void onInterrupt(Vibrato mod, TaskChain other) {}
        @Override protected void onTick(Vibrato mod) {}
        @Override public float getPriority(Vibrato mod) { return 0; }
        @Override public boolean isActive() { return true; }
        @Override public String getName() { return "crafting-handoff"; }
    }
    private static class Node extends Task {
        Task next;
        int ticks, stops;
        @Override protected void onStart(Vibrato mod) {}
        @Override protected Task onTick(Vibrato mod) { ticks++; return next; }
        @Override protected void onStop(Vibrato mod, Task interrupt) { stops++; }
        @Override protected boolean isEqual(Task other) { return this == other; }
        @Override protected String toDebugString() { return "container"; }
    }
    /** Models an output pickup followed by its next inventory-slot click. */
    private static final class Receiver extends ReceiveCraftingOutputSlotTask {
        boolean cursor;
        boolean stored;
        Receiver() { super(PlayerSlot.CRAFT_OUTPUT_SLOT, 1); }
        @Override protected void onStart(Vibrato mod) {}
        @Override protected boolean cursorHoldsOutput() { return cursor; }
        @Override protected Task onTick(Vibrato mod) {
            if (!cursor && !stored) cursor = true;
            else if (cursor) { cursor = false; stored = true; }
            return null;
        }
    }
    @Test void furnacePlacementCannotCloseCraftingScreenWhileOutputIsOnCursor() {
        Node smelting = new Node(), crafting = new Node(), placement = new Node();
        Receiver output = new Receiver();
        Chain chain = new Chain();
        smelting.next = crafting;
        crafting.next = output;
        smelting.tick(null, chain); // A furnace has just been picked onto the cursor.
        assertTrue(output.cursor);
        assertFalse(crafting.canBeInterruptedBy(null, placement));
        smelting.next = placement; // Parent sees the cursor item and requests placement.
        smelting.tick(null, chain);
        assertTrue(output.stored);
        assertEquals(0, crafting.stops, "onStop must not close the crafting screen before storage");
        assertEquals(0, placement.ticks);
        smelting.tick(null, chain);
        assertEquals(1, crafting.stops);
        assertEquals(1, placement.ticks);
    }
    @Test void nullHandoffAlsoDrainsOutputBeforeScreenCleanup() {
        Node crafting = new Node();
        Receiver output = new Receiver();
        Chain chain = new Chain();
        crafting.next = output;
        crafting.tick(null, chain);
        crafting.next = null;
        crafting.tick(null, chain);
        assertTrue(output.stored);
        assertTrue(output.canBeInterruptedBy(null, null));
        crafting.tick(null, chain);
        assertFalse(output.isActive());
    }
}
