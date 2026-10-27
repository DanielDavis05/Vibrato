package dev.vibrato.tasks.resources;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.CraftInInventoryTask;
import dev.vibrato.tasks.ResourceTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.CraftingRecipe;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.MiningRequirement;
import dev.vibrato.util.RecipeTarget;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;

public class CollectWheatTask extends ResourceTask {

    private final int _count;

    public CollectWheatTask(int targetCount) {
        super(Items.WHEAT, targetCount);
        _count = targetCount;
    }

    @Override
    protected boolean shouldAvoidPickingUp(Vibrato mod) {
        return false;
    }

    @Override
    protected void onResourceStart(Vibrato mod) {
        mod.getBlockTracker().trackBlock(Blocks.HAY_BLOCK);
    }

    @Override
    protected Task onResourceTick(Vibrato mod) {
        // We may have enough hay blocks to meet our needs.
        int potentialCount = mod.getItemStorage().getItemCount(Items.WHEAT) + 9 * mod.getItemStorage().getItemCount(Items.HAY_BLOCK);
        if (potentialCount >= _count) {
            setDebugState("Crafting wheat");
            return new CraftInInventoryTask(new RecipeTarget(Items.WHEAT, _count, CraftingRecipe.newShapedRecipe("wheat", new ItemTarget[]{new ItemTarget(Items.HAY_BLOCK, 1), null, null, null}, 9)));
        }
        if (mod.getBlockTracker().anyFound(Blocks.HAY_BLOCK) || mod.getEntityTracker().itemDropped(Items.HAY_BLOCK)) {
            return new MineAndCollectTask(Items.HAY_BLOCK, 99999999, new Block[]{Blocks.HAY_BLOCK}, MiningRequirement.HAND);
        }
        // Collect wheat
        return new CollectCropTask(new ItemTarget(Items.WHEAT, _count), new Block[]{Blocks.WHEAT}, Items.WHEAT_SEEDS);
    }

    @Override
    protected void onResourceStop(Vibrato mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(Blocks.HAY_BLOCK);
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectWheatTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Collecting " + _count + " wheat.";
    }

}
