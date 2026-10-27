package dev.vibrato.tasks.construction;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;
import dev.vibrato.tasks.movement.RunAwayFromPositionTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.ITaskRequiresGrounded;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.baritone.PlaceBlockSchematic;
import dev.vibrato.util.time.TimerGame;
import dev.vibrato.util.helpers.LookHelper;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.helpers.WorldHelper;
import dev.vibrato.util.progresscheck.MovementProgressChecker;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Destroy a block at a position.
 */
public class DestroyBlockTask extends Task implements ITaskRequiresGrounded {

    private final BlockPos _pos;
    private final MovementProgressChecker _moveChecker = new MovementProgressChecker(6, 0.1, 4, 0.01);
    private final TimeoutWanderTask _wanderTask = new TimeoutWanderTask(5, true);

    private final TimerGame _tryToMineTimer = new TimerGame(5);

    // For vines and stuff
    private boolean _wasClose = false;
    private boolean _manualBreaking;

    public DestroyBlockTask(BlockPos pos) {
        _pos = pos;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _manualBreaking = false;
        _moveChecker.reset();
        _tryToMineTimer.forceElapse();
        _wanderTask.resetWander();
        StorageHelper.closeScreen();

        mod.getBehaviour().push();
        // Avoid placing on top, to prevent our annoying "run away" bug
        mod.getBehaviour().avoidBlockPlacing(pos -> _pos.up().equals(pos));
    }

    @Override
    protected Task onTick(Vibrato mod) {
        boolean wasManualBreaking = _manualBreaking;
        _manualBreaking = false;
        if (wasManualBreaking) {
            // Release our own attack before aiming again. A held attack while the camera
            // turns can otherwise chew through the floor on the way to the intended block.
            mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
        }

        // Wander and check
        if (_wanderTask.isActive() && !_wanderTask.isFinished(mod)) {
            _moveChecker.reset();
            return _wanderTask;
        }
        if (!_moveChecker.check(mod)) {
            _moveChecker.reset();
            mod.getBlockTracker().requestBlockUnreachable(_pos);
            _wanderTask.resetWander();
            mod.getClientBaritone().getPathingBehavior().cancelEverything();
            return _wanderTask;
        }

        // do NOT break if we're standing above it and it's dangerous below...
        if (!WorldHelper.isSolid(mod, _pos.up()) && mod.getPlayer().getPos().y > _pos.getY() && _pos.isWithinDistance(mod.getPlayer().isOnGround()? mod.getPlayer().getPos() : mod.getPlayer().getPos().add(0, -1, 0), 0.89)) {
            if (WorldHelper.dangerousToBreakIfRightAbove(mod, _pos)) {
                setDebugState("It's dangerous to break as we're right above it, moving away and trying again.");
                return new RunAwayFromPositionTask(3, _pos.getY(), _pos);
            }
        }

        // We're trying to mine
        Optional<Rotation> reach = LookHelper.getReach(_pos);
        if (reach.isPresent()) {
            _tryToMineTimer.reset();
        }
        if (!_tryToMineTimer.elapsed()) {
            if (reach.isPresent() && (mod.getPlayer().isTouchingWater() || mod.getPlayer().isOnGround())) {
                setDebugState("Block in range, mining...");
                // Break the block, force it.
                if (!wasManualBreaking) {
                    // Losing process control alone leaves the old path moving until the next
                    // engine tick. Stop that path once before taking over a reachable block.
                    mod.getClientBaritone().getPathingBehavior().cancelEverything();
                }
                _manualBreaking = true;
                if (!LookHelper.isLookingAtNow(mod, _pos)) {
                    LookHelper.lookAt(mod, reach.get());
                }
                if (LookHelper.isLookingAt(mod, _pos) && LookHelper.isLookingAtNow(mod, _pos)) {
                    // Tool equip is handled in `PlayerInteractionFixChain`. Oof.
                    mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                }
            } else {
                setDebugState("Breaking the normal way.");
                if (!mod.getClientBaritone().getBuilderProcess().isActive()) {
                    // Try breaking normally.
                    mod.getClientBaritone().getCustomGoalProcess().onLostControl();
                    Debug.logInternal("Break Block: Restarting builder process");
                    mod.getClientBaritone().getBuilderProcess().build("destroy block", new PlaceBlockSchematic(Blocks.AIR), _pos);
                }
            }
        } else {
            setDebugState("Getting to block...");
            boolean isClose = _pos.isWithinDistance(mod.getPlayer().getPos(), 1);
            if (isClose != _wasClose) {
                mod.getClientBaritone().getCustomGoalProcess().onLostControl();
                _wasClose = isClose;
            }
            if (!mod.getClientBaritone().getCustomGoalProcess().isActive()) {
                mod.getClientBaritone().getBuilderProcess().onLostControl();
                // If we're close, go TO the block (potentially disrupts vines and stuff)
                mod.getClientBaritone().getCustomGoalProcess().setGoalAndPath(isClose? new GoalBlock(_pos) : new GoalNear(_pos, 1));
            }
        }

        return null;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        _manualBreaking = false;
        if (!Vibrato.inGame())
            return;
        mod.getBehaviour().pop();
        mod.getClientBaritone().getBuilderProcess().onLostControl();
        mod.getClientBaritone().getCustomGoalProcess().onLostControl();
        // Do not keep breaking.
        // Can lead to trouble, for example, if lava is right above the NEXT block.
        mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return WorldHelper.isAir(mod, _pos);//;
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof DestroyBlockTask task) {
            return task._pos.equals(_pos);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Destroy block at " + _pos.toShortString();
    }
}
