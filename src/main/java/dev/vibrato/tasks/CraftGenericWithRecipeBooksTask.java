package dev.vibrato.tasks;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.slot.ClickSlotTask;
import dev.vibrato.tasks.slot.ReceiveCraftingOutputSlotTask;
import dev.vibrato.tasks.slot.ThrowCursorTask;
import dev.vibrato.tasksystem.ITaskUsesCraftingGrid;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.RecipeTarget;
import dev.vibrato.util.helpers.ItemHelper;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.slots.CraftingTableSlot;
import dev.vibrato.util.slots.PlayerSlot;
import dev.vibrato.util.slots.Slot;
import dev.vibrato.util.time.TimerGame;
import net.minecraft.item.ItemStack;

import java.util.Arrays;
import java.util.Optional;

public class CraftGenericWithRecipeBooksTask extends Task implements ITaskUsesCraftingGrid {

    private final RecipeTarget _target;
    private final TimerGame _recipeWait = new TimerGame(5);
    private boolean _useManual;

    public CraftGenericWithRecipeBooksTask(RecipeTarget target) {
        _target = target;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _useManual = false;
        _recipeWait.reset();
    }

    @Override
    protected Task onTick(Vibrato mod) {

        

        if (_useManual) {
            return new CraftGenericManuallyTask(_target);
        }
        boolean bigCrafting = StorageHelper.isBigCraftingOpen();

        if (!bigCrafting && !StorageHelper.isPlayerInventoryOpen()) {
            // Make sure we're not in another screen before we craft,
            // otherwise crafting won't work
            StorageHelper.closeScreen();
            // Just to be safe
        }

        Slot outputSlot = bigCrafting ? CraftingTableSlot.OUTPUT_SLOT : PlayerSlot.CRAFT_OUTPUT_SLOT;
        ItemStack output = StorageHelper.getItemStackInSlot(outputSlot);
        if (_target.getOutputItem() == output.getItem() && mod.getItemStorage().getItemCount(_target.getOutputItem()) < _target.getTargetCount()) {
            _recipeWait.reset();
            setDebugState("Getting output");
            return new ReceiveCraftingOutputSlotTask(outputSlot, _target.getTargetCount());
        }  // TODO Migrate this back to Craft In Inventory

        // If a material is found in cursor, move it to the inventory.
        ItemStack cursor = StorageHelper.getItemStackInCursorSlot();


        // Crafting book REQUIRES that all materials be in the inventory. Materials CANNOT be in the cursor
        if (Arrays.stream(_target.getRecipe().getSlots()).anyMatch(target -> target.matches(cursor.getItem()))) {
            setDebugState("CURSOR HAS MATERIAL! Moving out.");
            Optional<Slot> toMoveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursor, false).or(() -> StorageHelper.getGarbageSlot(mod));
            if (toMoveTo.isPresent()) {
                return new ClickSlotTask(toMoveTo.get());
            } else {
                // If our inventory is full, but the material will be used for crafting, put it in the crafting grid.
                // TODO: Add as an alternative method to EnsureCursorSlotTask
                for (int recSlot = 0; recSlot < _target.getRecipe().getSlotCount(); ++recSlot) {
                    if (_target.getRecipe().getSlot(recSlot).matches(cursor.getItem())) {
                        Slot toMoveToPotential = bigCrafting? CraftingTableSlot.getInputSlot(recSlot, _target.getRecipe().isBig()) : PlayerSlot.getCraftInputSlot(recSlot);
                        ItemStack inRecipe = StorageHelper.getItemStackInSlot(toMoveToPotential);
                        if (ItemHelper.canStackTogether(cursor, inRecipe)) {
                            return new ClickSlotTask(toMoveToPotential);
                        }
                    }
                }
                // Crafting grid is full, and we still have the item, throw it away.
                return new ThrowCursorTask();
            }
        }

        // A server can reject a recipe-book request (for example when the
        // recipe has not been unlocked). Keep crafting by placing the already
        // collected ingredients directly instead of clicking forever.
        if (_recipeWait.elapsed()) {
            _useManual = true;
            setDebugState("Recipe book did not fill the grid; crafting manually");
            return new CraftGenericManuallyTask(_target);
        }

        // Request to fill in a recipe. Just piggy back off of the slot delay system.
        if (mod.getSlotHandler().canDoSlotAction()) {
            mod.getSlotHandler().registerSlotAction();
            StorageHelper.instantFillRecipeViaBook(mod, _target.getRecipe(), _target.getOutputItem(), true);
        }


        setDebugState("Waiting for recipe book click...");
        return null;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof CraftGenericWithRecipeBooksTask task) {
            return task._target.equals(_target);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Crafting (w/ RECIPE): " + _target;
    }
}
