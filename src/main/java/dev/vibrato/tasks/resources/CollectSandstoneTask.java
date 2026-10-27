package dev.vibrato.tasks.resources;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.CraftInInventoryTask;
import dev.vibrato.tasks.ResourceTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class CollectSandstoneTask extends ResourceTask {

    private final int _count;

    public CollectSandstoneTask(int targetCount) {
        super(Items.SANDSTONE, targetCount);
        _count = targetCount;
    }

    @Override
    protected boolean shouldAvoidPickingUp(Vibrato mod) {
        return false;
    }

    @Override
    protected void onResourceStart(Vibrato mod) {

    }

    @Override
    protected Task onResourceTick(Vibrato mod) {
        if (mod.getItemStorage().getItemCount(Items.SAND) >= 4) {
            int target = mod.getItemStorage().getItemCount(Items.SANDSTONE) + 1;
            ItemTarget s = new ItemTarget(Items.SAND, 1);
            return new CraftInInventoryTask(new RecipeTarget(Items.SANDSTONE, target, CraftingRecipe.newShapedRecipe("sandstone", new ItemTarget[]{s, s, s, s}, 1)));
        }
        return new MineAndCollectTask(new ItemTarget(new Item[]{Items.SANDSTONE, Items.SAND}), new Block[]{Blocks.SANDSTONE, Blocks.SAND}, MiningRequirement.WOOD).forceDimension(Dimension.OVERWORLD);
    }

    @Override
    protected void onResourceStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectSandstoneTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Collecting " + _count + " sandstone.";
    }
}
