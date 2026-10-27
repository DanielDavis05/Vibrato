package dev.vibrato.tasks.resources;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.container.CraftInTableTask;
import dev.vibrato.tasks.ResourceTask;
import dev.vibrato.tasks.container.SmeltInFurnaceTask;
import dev.vibrato.tasks.movement.DefaultGoToDimensionTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.*;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;

public class CollectGoldIngotTask extends ResourceTask {

    private final int _count;

    public CollectGoldIngotTask(int count) {
        super(Items.GOLD_INGOT, count);
        _count = count;
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
        if (WorldHelper.getCurrentDimension() == Dimension.OVERWORLD) {
            return new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(Items.GOLD_INGOT, _count), new ItemTarget(Items.RAW_GOLD, _count)));
        } else if (WorldHelper.getCurrentDimension() == Dimension.NETHER) {
            // If we have enough nuggets, craft them.
            int nuggs = mod.getItemStorage().getItemCount(Items.GOLD_NUGGET);
            int nuggs_needed = _count * 9 - mod.getItemStorage().getItemCount(Items.GOLD_INGOT) * 9;
            if (nuggs >= nuggs_needed) {
                ItemTarget n = new ItemTarget(Items.GOLD_NUGGET);
                CraftingRecipe recipe = CraftingRecipe.newShapedRecipe("gold_ingot", new ItemTarget[]{
                        n, n, n, n, n, n, n, n, n
                }, 1);
                return new CraftInTableTask(new RecipeTarget(Items.GOLD_INGOT, _count, recipe));
            }
            // Mine nuggets
            return new MineAndCollectTask(new ItemTarget(Items.GOLD_NUGGET, _count * 9), new Block[]{Blocks.NETHER_GOLD_ORE}, MiningRequirement.WOOD);
        } else {
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }
    }

    @Override
    protected void onResourceStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectGoldIngotTask && ((CollectGoldIngotTask) other)._count == _count;
    }

    @Override
    protected String toDebugStringName() {
        return "Collecting " + _count + " gold.";
    }
}
