package dev.vibrato.tasks.resources;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.container.CraftInTableTask;
import dev.vibrato.tasks.ResourceTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.CraftingRecipe;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.MiningRequirement;
import dev.vibrato.util.RecipeTarget;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;

// TODO: This can technically be removed, as it's a mine task followed by a collect task.
public class CollectHayBlockTask extends ResourceTask {

    private final int _count;

    public CollectHayBlockTask(int count) {
        super(Items.HAY_BLOCK, count);
        _count = count;
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

        if (mod.getBlockTracker().anyFound(Blocks.HAY_BLOCK)) {
            return new MineAndCollectTask(Items.HAY_BLOCK, _count, new Block[]{Blocks.HAY_BLOCK}, MiningRequirement.HAND);
        }

        ItemTarget w = new ItemTarget(Items.WHEAT, 1);
        return new CraftInTableTask(new RecipeTarget(Items.HAY_BLOCK, _count, CraftingRecipe.newShapedRecipe("hay_block", new ItemTarget[]{w, w, w, w, w, w, w, w, w}, 1)));
    }

    @Override
    protected void onResourceStop(Vibrato mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(Blocks.HAY_BLOCK);
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectHayBlockTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Collecting " + _count + " hay blocks.";
    }
}
