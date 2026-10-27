package dev.vibrato.tasks.life;

import dev.vibrato.Vibrato;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.movement.GetWithinRangeOfBlockTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.LookHelper;
import dev.vibrato.util.time.TimerGame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.Blocks;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Catches a number of fish: walks to water, casts the rod, reels in when the bobber is pulled under, and recasts.
 * Bounded by a per-fish time allowance.
 */
public class FishingTask extends Task {

    // The bobber sinks with a sharp negative vertical velocity when a fish bites.
    private static final double BITE_VELOCITY = -0.04;
    private static final double CAST_RANGE = 3;
    private static final double WAIT_SECONDS = 60;
    private static final Item[] FISH = {Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH};

    private final int _fishWanted;
    private final TimerGame _budget;
    private final TimerGame _biteTimeout = new TimerGame(WAIT_SECONDS);
    private int _startCount;
    private BlockPos _spot;

    public FishingTask(int fishWanted) {
        _fishWanted = fishWanted;
        _budget = new TimerGame(fishWanted * 120.0);
    }

    @Override
    protected void onStart(Vibrato mod) {
        mod.getBlockTracker().trackBlock(Blocks.WATER);
        _startCount = countFish(mod);
        _budget.reset();
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (!mod.getItemStorage().hasItem(Items.FISHING_ROD)) {
            setDebugState("Getting a fishing rod");
            return TaskCatalogue.getItemTask(Items.FISHING_ROD, 1);
        }

        FishingBobberEntity bobber = mod.getPlayer().fishHook;
        if (bobber == null) {
            Optional<BlockPos> water = mod.getBlockTracker().getNearestTracking(mod.getPlayer().getPos(), Blocks.WATER);
            if (water.isEmpty()) {
                setDebugState("Looking for water");
                return new TimeoutWanderTask();
            }
            _spot = water.get();
            if (!_spot.isWithinDistance(mod.getPlayer().getPos(), CAST_RANGE + 2)) {
                setDebugState("Walking to water");
                return new GetWithinRangeOfBlockTask(_spot, (int) CAST_RANGE);
            }
            mod.getSlotHandler().forceEquipItem(Items.FISHING_ROD);
            LookHelper.lookAt(mod, _spot);
            // Cast only once the look has settled on the water; the look is smoothed over several ticks.
            if (LookHelper.isLookingAt(mod, _spot)) {
                setDebugState("Casting");
                MinecraftClient.getInstance().interactionManager.interactItem(mod.getPlayer(), Hand.MAIN_HAND);
                _biteTimeout.reset();
            } else {
                setDebugState("Aiming at the water");
            }
            return null;
        }

        if (bobber.getVelocity().y < BITE_VELOCITY && bobber.isTouchingWater()) {
            setDebugState("Fish on the line, reeling in");
            reelIn(mod);
        } else if (_biteTimeout.elapsed()) {
            setDebugState("Nothing biting, reeling in to recast");
            reelIn(mod);
        } else {
            setDebugState("Waiting for a bite");
        }
        return null;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(Blocks.WATER);
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return countFish(mod) - _startCount >= _fishWanted || _budget.elapsed();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof FishingTask task && task._fishWanted == _fishWanted;
    }

    @Override
    protected String toDebugString() {
        return "Fishing for " + _fishWanted + " fish";
    }

    private void reelIn(Vibrato mod) {
        MinecraftClient.getInstance().interactionManager.interactItem(mod.getPlayer(), Hand.MAIN_HAND);
    }

    private static int countFish(Vibrato mod) {
        return mod.getItemStorage().getItemCount(FISH);
    }
}
