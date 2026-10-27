package dev.vibrato.tasks.explore;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Explores until a villager or a village block (bell, hay bale, dirt path) is seen, then logs the spot as {@code village}.
 * <p>
 * Limits: exploration uses the explore process with a short wander radius per attempt, so the activity budget is the cap.
 * A village seen only at the edge of loaded chunks is logged where the bot is standing when it sees it.
 */
public class FindVillageTask extends Task {

    private static final Block[] VILLAGE_BLOCKS = {Blocks.BELL, Blocks.HAY_BLOCK, Blocks.DIRT_PATH};
    private static final float WANDER_DISTANCE = 200;

    private boolean _found;

    @Override
    protected void onStart(Vibrato mod) {
        _found = false;
        mod.getBlockTracker().trackBlock(VILLAGE_BLOCKS);
    }

    @Override
    protected Task onTick(Vibrato mod) {
        Optional<BlockPos> spot = villageSpot(mod);
        if (spot.isPresent()) {
            if (DiscoveryLog.add("village", spot.get())) {
                setDebugState("Found a village at " + spot.get().toShortString());
            }
            _found = true;
            return null;
        }
        setDebugState("Exploring for a village");
        return new TimeoutWanderTask(WANDER_DISTANCE, true);
    }

    private static Optional<BlockPos> villageSpot(Vibrato mod) {
        Entity villager = mod.getEntityTracker().getClosestEntity(VillagerEntity.class).orElse(null);
        if (villager != null) {
            return Optional.of(villager.getBlockPos());
        }
        return mod.getBlockTracker().getNearestTracking(VILLAGE_BLOCKS);
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(VILLAGE_BLOCKS);
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _found;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof FindVillageTask;
    }

    @Override
    protected String toDebugString() {
        return "Finding a village";
    }
}
