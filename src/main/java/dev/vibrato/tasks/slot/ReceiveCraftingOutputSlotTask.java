package dev.vibrato.tasks.slot;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.ITaskUsesCraftingGrid;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.ItemHelper;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.slots.CraftingTableSlot;
import dev.vibrato.util.slots.PlayerSlot;
import dev.vibrato.util.slots.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public class ReceiveCraftingOutputSlotTask extends Task implements ITaskUsesCraftingGrid {

    private final int _toTake;
    private final Slot _slot;

    public ReceiveCraftingOutputSlotTask(Slot slot, int toTake) {
        _slot = slot;
        _toTake = toTake;
    }
    public ReceiveCraftingOutputSlotTask(Slot slot, boolean all) {
        this(slot, all? Integer.MAX_VALUE : 1);
    }

    @Override
    protected void onStart(Vibrato mod) {

    }

    @Override
    protected Task onTick(Vibrato mod) {
        ItemStack inOutput = StorageHelper.getItemStackInSlot(_slot);
        ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
        boolean cursorSlotFree = cursor.isEmpty();
        if(!cursorSlotFree && !ItemHelper.canStackTogether(inOutput, cursor)) {
            return new EnsureFreeCursorSlotTask();
        }
        int craftCount = inOutput.getCount() * getCraftMultipleCount(mod);
        int weWantToAddToInventory = _toTake - mod.getItemStorage().getItemCountInventoryOnly(inOutput.getItem());
        boolean takeAll = weWantToAddToInventory >= craftCount;
        if (takeAll && mod.getItemStorage().getSlotThatCanFitInPlayerInventory(inOutput, true).isPresent()) {
            setDebugState("Quick moving output");
            return new ClickSlotTask(_slot, SlotActionType.QUICK_MOVE);
        }
        setDebugState("Picking up output");
        return new ClickSlotTask(_slot);
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof ReceiveCraftingOutputSlotTask task) {
            return task._slot.equals(_slot) && task._toTake == _toTake;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Receiving output";
    }

    // How many multiples of the current crafting recipe can we craft?
    private static int getCraftMultipleCount(Vibrato mod) {
        int minNonZero = Integer.MAX_VALUE;
        boolean found = false;
        for (Slot check : (StorageHelper.isBigCraftingOpen()? CraftingTableSlot.INPUT_SLOTS :PlayerSlot.CRAFT_INPUT_SLOTS)) {
            ItemStack stack = StorageHelper.getItemStackInSlot(check);
            if (!stack.isEmpty()) {
                minNonZero = Math.min(stack.getCount(), minNonZero);
                found = true;
            }
        }
        if (!found)
            return 0;
        return minNonZero;
    }
}
