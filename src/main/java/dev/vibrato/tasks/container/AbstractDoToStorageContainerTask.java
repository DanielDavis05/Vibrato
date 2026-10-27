package dev.vibrato.tasks.container;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.InteractWithBlockTask;
import dev.vibrato.tasks.construction.DestroyBlockTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.trackers.storage.ContainerCache;
import dev.vibrato.trackers.storage.ContainerType;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Opens a STORAGE container and does whatever you want inside of it
 */
public abstract class AbstractDoToStorageContainerTask extends Task {

    private ContainerType _currentContainerType = null;

    @Override
    protected void onStart(Vibrato mod) {

    }

    @Override
    protected Task onTick(Vibrato mod) {
        Optional<BlockPos> containerTarget = getContainerTarget();

        // No container found
        if (containerTarget.isEmpty()) {
            setDebugState("Wandering");
            _currentContainerType = null;
            return onSearchWander(mod);
        }

        BlockPos targetPos = containerTarget.get();

        // We're open
        if (_currentContainerType != null && ContainerType.screenHandlerMatches(_currentContainerType)) {

            // Optional<BlockPos> lastInteracted = mod.getItemStorage().getLastBlockPosInteraction();
            //if (lastInteracted.isPresent() && lastInteracted.get().equals(targetPos)) {
                Optional<ContainerCache> cache = mod.getItemStorage().getContainerAtPosition(targetPos);
                if (cache.isPresent()) {
                    return onContainerOpenSubtask(mod, cache.get());
                }
            //}
        }

        // Get to the container
        if (mod.getChunkTracker().isChunkLoaded(targetPos)) {
            Block type = mod.getWorld().getBlockState(targetPos).getBlock();
            _currentContainerType = ContainerType.getFromBlock(type);
        }
        if (WorldHelper.isChest(mod, targetPos) && WorldHelper.isSolid(mod, targetPos.up()) && WorldHelper.canBreak(mod, targetPos.up())) {
            setDebugState("Clearing block above chest");
            return new DestroyBlockTask(targetPos.up());
        }
        setDebugState("Opening container: " + targetPos.toShortString());
        return new InteractWithBlockTask(targetPos);
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    protected abstract Optional<BlockPos> getContainerTarget();

    protected abstract Task onContainerOpenSubtask(Vibrato mod, ContainerCache containerCache);

    // Virtual
    // TODO: Interface this
    protected Task onSearchWander(Vibrato mod) {
        return new TimeoutWanderTask();
    }
}
