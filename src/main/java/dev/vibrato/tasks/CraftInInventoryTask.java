package dev.vibrato.tasks;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.resources.CollectRecipeCataloguedResourcesTask;
import dev.vibrato.tasks.slot.ReceiveCraftingOutputSlotTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.RecipeTarget;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.slots.PlayerSlot;
import dev.vibrato.util.slots.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Crafts an item within the 2x2 inventory crafting grid.
 */
public class CraftInInventoryTask extends ResourceTask {

    private final RecipeTarget _target;
    private final boolean _collect;
    private final boolean _ignoreUncataloguedSlots;
    private boolean _fullCheckFailed = false;

    public CraftInInventoryTask(RecipeTarget target, boolean collect, boolean ignoreUncataloguedSlots) {
        super(new ItemTarget(target.getOutputItem(), target.getTargetCount()));
        _target = target;
        _collect = collect;
        _ignoreUncataloguedSlots = ignoreUncataloguedSlots;
    }

    public CraftInInventoryTask(RecipeTarget target) {
        this(target, true, false);
    }

    @Override
    protected boolean shouldAvoidPickingUp(Vibrato mod) {
        return false;
    }

    @Override
    protected void onResourceStart(Vibrato mod) {
        mod.getBehaviour().push();
        // Our inventory slots are here for conversion
        int recSlot = 0;
        for (Slot slot : PlayerSlot.CRAFT_INPUT_SLOTS) {
            ItemTarget valid = _target.getRecipe().getSlot(recSlot++);
            mod.getBehaviour().markSlotAsConversionSlot(slot, stack -> valid.matches(stack.getItem()));
        }

        _fullCheckFailed = false;
        StorageHelper.closeScreen(); // Just to be safe I guess
    }

    @Override
    protected Task onResourceTick(Vibrato mod) {
        // Grab from output FIRST
        if (StorageHelper.isPlayerInventoryOpen()) {
            if (StorageHelper.getItemStackInCursorSlot().isEmpty()) {
                Item outputItem = StorageHelper.getItemStackInSlot(PlayerSlot.CRAFT_OUTPUT_SLOT).getItem();
                for (ItemTarget target : _itemTargets) {
                    if (target.matches(outputItem)) {
                        return new ReceiveCraftingOutputSlotTask(PlayerSlot.CRAFT_OUTPUT_SLOT, target.getTargetCount());
                    }
                }
            }
        }

        ItemTarget toGet = _itemTargets[0];
        Item toGetItem = toGet.getMatches()[0];
        if (_collect && !StorageHelper.hasRecipeMaterialsOrTarget(mod, _target)) {
            // Collect recipe materials
            setDebugState("Collecting materials");
            return collectRecipeSubTask(mod);
        }

        // No need to free inventory, output gets picked up.

            setDebugState("Crafting in inventory... for " + toGet);
            return mod.getModSettings().shouldUseCraftingBookToCraft() ? new CraftGenericWithRecipeBooksTask(_target) : new CraftGenericManuallyTask(_target);


    }

    @Override
    protected void onResourceStop(Vibrato mod, Task interruptTask) {
        mod.getBehaviour().pop();
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        if (other instanceof CraftInInventoryTask task) {
            if (!task._target.equals(_target)) return false;
            return isCraftingEqual(task);
        }
        return false;
    }

    @Override
    protected String toDebugStringName() {
        return toCraftingDebugStringName() + " " + _target;
    }

    // virtual. By default assumes subtasks are CATALOGUED (in TaskCatalogue.java)
    protected Task collectRecipeSubTask(Vibrato mod) {
        return new CollectRecipeCataloguedResourcesTask(_ignoreUncataloguedSlots, _target);
    }

    protected String toCraftingDebugStringName() {
        return "Craft 2x2 Task";
    }

    protected boolean isCraftingEqual(CraftInInventoryTask other) {
        return true;
    }

    public RecipeTarget getRecipeTarget() {
        return _target;
    }
}
