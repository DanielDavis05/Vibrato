package dev.vibrato.tasks.resources;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.CraftInInventoryTask;
import dev.vibrato.tasks.ResourceTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class CollectAmethystBlockTask extends ResourceTask {

    private final int _count;

    public CollectAmethystBlockTask(int targetCount) {
        super(Items.AMETHYST_BLOCK, targetCount);
        _count = targetCount;
    }

    @Override
    protected boolean shouldAvoidPickingUp(Vibrato mod) {
        return false;
    }

    @Override
    protected void onResourceStart(Vibrato mod) {
        mod.getBlockTracker().trackBlock(Blocks.BUDDING_AMETHYST);

        // Bot will not break Budding Amethyst
        mod.getBehaviour().push();
        mod.getBehaviour().avoidBlockBreaking(blockPos -> {
            BlockState s = mod.getWorld().getBlockState(blockPos);
            return s.getBlock() == Blocks.BUDDING_AMETHYST;
        });
    }

    @Override
    protected Task onResourceTick(Vibrato mod) {
        if (mod.getItemStorage().getItemCount(Items.AMETHYST_SHARD) >= 4) {
            int target = mod.getItemStorage().getItemCount(Items.AMETHYST_BLOCK) + 1;
            ItemTarget s = new ItemTarget(Items.AMETHYST_SHARD, 1);
            return new CraftInInventoryTask(new RecipeTarget(Items.AMETHYST_BLOCK, target, CraftingRecipe.newShapedRecipe("amethyst_block", new ItemTarget[]{s, s, s, s}, 1)));
        }
        return new MineAndCollectTask(new ItemTarget(new Item[]{Items.AMETHYST_BLOCK, Items.AMETHYST_SHARD}), new Block[]{Blocks.AMETHYST_BLOCK, Blocks.AMETHYST_CLUSTER}, MiningRequirement.WOOD).forceDimension(Dimension.OVERWORLD);
    }

    @Override
    protected void onResourceStop(Vibrato mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(Blocks.BUDDING_AMETHYST);
        mod.getBehaviour().pop();
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectAmethystBlockTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Collecting " + _count + " Amethyst Blocks.";
    }
}
