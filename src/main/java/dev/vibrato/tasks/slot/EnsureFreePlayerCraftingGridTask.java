package dev.vibrato.tasks.slot;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.slots.PlayerSlot;
import dev.vibrato.util.slots.Slot;
import net.minecraft.item.ItemStack;

public class EnsureFreePlayerCraftingGridTask extends Task {
    @Override
    protected void onStart(Vibrato mod) {

    }

    @Override
    protected Task onTick(Vibrato mod) {
        setDebugState("Clearing the 2x2 crafting grid");
        for(Slot slot : PlayerSlot.CRAFT_INPUT_SLOTS) {
            ItemStack items = StorageHelper.getItemStackInSlot(slot);
            ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
            if(!cursor.isEmpty()){
                return new EnsureFreeCursorSlotTask();
            }
            if(!items.isEmpty()){
                return new ClickSlotTask(slot);
            }
        }
        return null;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof EnsureFreePlayerCraftingGridTask;
    }

    @Override
    protected String toDebugString() {
        return "Breaking the crafting grid";
    }
}
