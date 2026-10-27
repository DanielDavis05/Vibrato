package dev.vibrato.tasks.resources;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.InteractWithBlockTask;
import dev.vibrato.tasks.ResourceTask;
import dev.vibrato.tasks.construction.PlaceBlockTask;
import dev.vibrato.tasks.movement.GetCloseToBlockTask;
import dev.vibrato.tasks.movement.SearchChunkForBlockTask;
import dev.vibrato.tasks.squashed.CataloguedResourceTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

public class CollectHoneycombTask extends ResourceTask {
    private final boolean _campfire;
    private final int _count;
    private BlockPos _nest;
    public CollectHoneycombTask(int targetCount) {
        super(Items.HONEYCOMB, targetCount);
        _campfire = true;
        _count = targetCount;
    }

    public CollectHoneycombTask(int targetCount, boolean useCampfire) {
        super(Items.HONEYCOMB, targetCount);
        _campfire = useCampfire;
        _count = targetCount;
    }

    @Override
    protected void onResourceStart(Vibrato mod) {
        mod.getBehaviour().push();
        mod.getBlockTracker().trackBlock(Blocks.BEE_NEST);
    }

    @Override
    protected Task onResourceTick(Vibrato mod) {
        if (_nest == null) {
            Optional<BlockPos> getNearestNest = mod.getBlockTracker().getNearestTracking(Blocks.BEE_NEST);
            if (getNearestNest.isPresent()) _nest = getNearestNest.get();
        }
        // If we are STILL null
        if (_nest == null) {
            if (_campfire && !mod.getItemStorage().hasItemInventoryOnly(Items.CAMPFIRE)) {
                // May as well get a campfire
                setDebugState("Can't find nest, getting campfire first...");
                return new CataloguedResourceTask(new ItemTarget(Items.CAMPFIRE, 1));
            }
            setDebugState("Alright, we're searching");
            return new SearchChunkForBlockTask(Blocks.BEE_NEST);
        }
        if (_campfire && !isCampfireUnderNest(mod, _nest)) {
            if (!mod.getItemStorage().hasItemInventoryOnly(Items.CAMPFIRE)) {
                setDebugState("Getting a campfire");
                return new CataloguedResourceTask(new ItemTarget(Items.CAMPFIRE, 1));
            }
            setDebugState("Placing campfire");
            return new PlaceBlockTask(_nest.down(2), Blocks.CAMPFIRE);
        }
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.SHEARS)) {
            setDebugState("Getting shears");
            return new CataloguedResourceTask(new ItemTarget(Items.SHEARS, 1));
        }
        if (mod.getWorld().getBlockState(_nest).get(Properties.HONEY_LEVEL) != 5) {
            if (!_nest.isWithinDistance(mod.getPlayer().getPos(), 20)) {
                setDebugState("Getting close to nest");
                return new GetCloseToBlockTask(_nest);
            }
            setDebugState("Waiting for nest to get honey...");
            return null;
        }
        return new InteractWithBlockTask(Items.SHEARS, _nest);
    }

    @Override
    protected void onResourceStop(Vibrato mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(Blocks.BEE_NEST);
        mod.getBehaviour().pop();
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectHoneycombTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Collecting " + _count + " Honeycombs " + (_campfire ? "Peacefully" : "Recklessly");
    }

    @Override
    protected boolean shouldAvoidPickingUp(Vibrato mod) {
        return false;
    }

    private boolean isCampfireUnderNest(Vibrato mod, BlockPos pos) {
        for(BlockPos underPos : WorldHelper.scanRegion(mod, pos.down(6), pos.down())) {
            if (mod.getWorld().getBlockState(underPos).getBlock() == Blocks.CAMPFIRE)
                return true;
        }
        return false;
    }
}
