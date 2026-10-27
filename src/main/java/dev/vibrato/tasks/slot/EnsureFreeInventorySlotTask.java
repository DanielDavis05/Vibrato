package dev.vibrato.tasks.slot;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.ItemHelper;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.slots.Slot;
import net.minecraft.item.ItemStack;

import java.util.Optional;

public class EnsureFreeInventorySlotTask extends Task {
    @Override
    protected void onStart(Vibrato mod) {

    }

    @Override
    protected Task onTick(Vibrato mod) {
        ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot();
        if (cursorStack.isEmpty() || !ItemHelper.canThrowAwayStack(mod, cursorStack)) {
            Optional<Slot> toThrow = StorageHelper.getGarbageSlot(mod);
            if (toThrow.isPresent()) {
                setDebugState("Finding garbage");
                return new ClickSlotTask(toThrow.get());
            } else {
                setDebugState("NO THROWAWAYABLE!");
                return null;
            }
        } else {
            setDebugState("Throwing cursor");
            return new ThrowCursorTask();
        }
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task obj) {
        return obj instanceof EnsureFreeInventorySlotTask;
    }

    @Override
    protected String toDebugString() {
        return "Ensuring inventory is free";
    }
}
