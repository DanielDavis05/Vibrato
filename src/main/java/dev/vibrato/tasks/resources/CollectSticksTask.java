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
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

public class CollectSticksTask extends ResourceTask {

    private final int _targetCount;

    public CollectSticksTask(int targetCount) {
        super(Items.STICK, targetCount);
        _targetCount = targetCount;
    }

    @Override
    protected boolean shouldAvoidPickingUp(Vibrato mod) {
        return false;
    }

    @Override
    protected void onResourceStart(Vibrato mod) {
        mod.getBehaviour().push();
        mod.getBlockTracker().trackBlock(Blocks.DEAD_BUSH);
    }

    @Override
    protected Task onResourceTick(Vibrato mod) {
        Optional<BlockPos> nearestBush = mod.getBlockTracker().getNearestTracking(Blocks.DEAD_BUSH);
        // If there's a dead bush within range, go get it
        if (nearestBush.isPresent() && nearestBush.get().isWithinDistance(mod.getPlayer().getPos(), 20)) {
            return new MineAndCollectTask(Items.DEAD_BUSH, 999999, new Block[]{Blocks.DEAD_BUSH}, MiningRequirement.HAND);
        }
        // else craft from wood
        return new CraftInInventoryTask(new RecipeTarget(Items.STICK, _targetCount, CraftingRecipe.newShapedRecipe("sticks", new ItemTarget[]{new ItemTarget("planks"), null, new ItemTarget("planks"), null},4)));
    }

    @Override
    protected void onResourceStop(Vibrato mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(Blocks.DEAD_BUSH);
        mod.getBehaviour().pop();
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectSticksTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Crafting " + _targetCount + " sticks";
    }
}
