package dev.vibrato.tasks.resources;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.ResourceTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.MiningRequirement;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;

public class CollectCobblestoneTask extends ResourceTask {

    private final int _count;

    public CollectCobblestoneTask(int targetCount) {
        super(Items.COBBLESTONE, targetCount);
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
        return new MineAndCollectTask(Items.COBBLESTONE, 1, new Block[]{Blocks.STONE, Blocks.COBBLESTONE}, MiningRequirement.WOOD);
    }

    @Override
    protected void onResourceStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        if (other instanceof CollectCobblestoneTask task) {
            return task._count == _count;
        }
        return false;
    }

    @Override
    protected String toDebugStringName() {
        return "Collect Cobblestone";
    }
}
