package dev.vibrato.tasks.life;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.DoToClosestBlockTask;
import dev.vibrato.tasks.ResourceTask;
import dev.vibrato.tasks.construction.DestroyBlockTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

/**
 * Harvests sugar cane, pumpkins or melons without destroying what regrows them. Sugar cane keeps its bottom block,
 * and pumpkins and melons keep their stem, so the crop comes back on its own.
 */
public class HarvestAndReplantTask extends ResourceTask {

    private final Item _crop;
    private final Block _block;
    private final int _count;

    public HarvestAndReplantTask(Item crop, int count) {
        super(crop, count);
        _crop = crop;
        _count = count;
        _block = crop == Items.MELON_SLICE ? Blocks.MELON : Block.getBlockFromItem(crop);
        if (_block == Blocks.AIR) {
            throw new IllegalArgumentException("Not a harvestable crop: " + crop);
        }
    }

    @Override
    protected boolean shouldAvoidPickingUp(Vibrato mod) {
        return false;
    }

    @Override
    protected void onResourceStart(Vibrato mod) {
        mod.getBlockTracker().trackBlock(_block);
    }

    @Override
    protected Task onResourceTick(Vibrato mod) {
        if (!mod.getBlockTracker().anyFound(_block)) {
            setDebugState("Searching for " + _block.getTranslationKey());
            return new TimeoutWanderTask();
        }
        setDebugState("Harvesting " + _block.getTranslationKey());
        return new DoToClosestBlockTask(DestroyBlockTask::new, pos -> isHarvestable(mod, pos), _block);
    }

    @Override
    protected void onResourceStop(Vibrato mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(_block);
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof HarvestAndReplantTask task && task._crop == _crop && task._count == _count;
    }

    @Override
    protected String toDebugStringName() {
        return "Harvesting " + _count + " " + _crop.getTranslationKey();
    }

    private boolean isHarvestable(Vibrato mod, BlockPos pos) {
        if (_block == Blocks.SUGAR_CANE) {
            // Only the cane above the base block; the base is what regrows.
            return mod.getWorld().getBlockState(pos.down()).isOf(Blocks.SUGAR_CANE) && WorldHelper.canBreak(mod, pos);
        }
        return WorldHelper.canBreak(mod, pos);
    }
}
