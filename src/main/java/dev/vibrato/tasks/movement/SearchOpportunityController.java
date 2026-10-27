package dev.vibrato.tasks.movement;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.StorageHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.Blocks;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Preserves a search's opportunity budget across navigation legs and interrupts. */
public final class SearchOpportunityController {
    private final SearchDetourBudget budget = new SearchDetourBudget();
    private final SearchActiveClock clock = new SearchActiveClock();
    private Vec3d lastPosition;
    private SearchOpportunityTask task;
    private boolean release;

    /** Call only while the search owns the main task chain. */
    public Task tick(Vibrato mod, boolean canInterrupt) {
        if (!dev.vibrato.human.HumanComponents.behavior()) {
            release = task != null;
            if (task != null) end(mod);
            return null;
        }
        double elapsed = clock.tick();
        release = false;
        if (task != null) {
            if (!safe(mod) || !budget.canContinue(elapsed) || task.isFinished(mod)) {
                if (!task.canBeInterruptedBy(mod, null)) return task;
                end(mod);
                release = true;
                return null;
            }
            return task;
        }
        double distance = lastPosition == null ? Double.POSITIVE_INFINITY : mod.getPlayer().getPos().squaredDistanceTo(lastPosition);
        if (canInterrupt && mod.getPlayer().age % 20 == 0 && budget.canStart(elapsed, distance) && safe(mod)) {
            SearchOpportunityTask found = SearchOpportunityTask.find(mod);
            if (found != null) {
                task = found;
                budget.start(elapsed);
                mod.getClientBaritone().getExploreProcess().onLostControl();
                mod.getClientBaritone().getCustomGoalProcess().onLostControl();
                mod.getClientBaritone().getPathingBehavior().cancelEverything();
                // Let the previous child stop before starting this task.
                return null;
            }
        }
        return null;
    }

    public boolean ownsControl() { return task != null || release; }

    /** Called when the owner is interrupted or stopped; elapsed search time stays intact. */
    public void stop(Vibrato mod) {
        if (task != null && mod.getPlayer() != null) end(mod);
        release = false;
    }

    private void end(Vibrato mod) {
        budget.end(clock.elapsed());
        lastPosition = mod.getPlayer().getPos();
        task = null;
    }

    private boolean safe(Vibrato mod) {
        var player = mod.getPlayer();
        if (MinecraftClient.getInstance().currentScreen != null || !StorageHelper.getItemStackInCursorSlot().isEmpty()
                || !player.isOnGround() || player.isTouchingWater() || player.isInLava() || player.isOnFire()
                || player.getHealth() < player.getMaxHealth() * 0.75
                || player.getVelocity().horizontalLengthSquared() > 0.09
                || (task == null && mod.getControllerExtras().isBreakingBlock())
                || mod.getClientBaritone().getBuilderProcess().isActive()) return false;
        if (mod.getEntityTracker().getHostiles().stream().anyMatch(entity -> entity.isAlive() && entity.isInRange(player, 10))) return false;
        BlockPos feet = player.getBlockPos();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockPos below = feet.add(x, -1, z);
            BlockPos standing = feet.add(x, 0, z);
            var floor = mod.getWorld().getBlockState(below);
            var atFeet = mod.getWorld().getBlockState(standing);
            if (!floor.isSolidBlock(mod.getWorld(), below) || floor.isOf(Blocks.MAGMA_BLOCK)
                    || atFeet.getBlock() instanceof AbstractFireBlock || atFeet.isOf(Blocks.CACTUS)
                    || atFeet.isOf(Blocks.SWEET_BERRY_BUSH)
                    || !mod.getWorld().getFluidState(standing).isEmpty()
                    || !mod.getWorld().getFluidState(standing.up()).isEmpty()) return false;
        }
        return true;
    }
}
