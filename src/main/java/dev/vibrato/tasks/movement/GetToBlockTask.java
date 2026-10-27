package dev.vibrato.tasks.movement;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.ITaskRequiresGrounded;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.Dimension;
import dev.vibrato.util.helpers.WorldHelper;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.util.math.BlockPos;

public class GetToBlockTask extends CustomBaritoneGoalTask implements ITaskRequiresGrounded {

    private final BlockPos _position;
    private final boolean _preferStairs;
    private final Dimension _dimension;

    public GetToBlockTask(BlockPos position, boolean preferStairs) {
        this(position, preferStairs, null);
    }
    public GetToBlockTask(BlockPos position, Dimension dimension) {
        this(position, false, dimension);
    }

    public GetToBlockTask(BlockPos position, boolean preferStairs, Dimension dimension) {
        _dimension = dimension;
        _position = position.toImmutable();
        _preferStairs = preferStairs;
    }

    public GetToBlockTask(BlockPos position) {
        this(position, false);
    }
    @Override
    protected Task onTick(Vibrato mod) {
        if (_dimension != null && WorldHelper.getCurrentDimension() != _dimension) {
            return new DefaultGoToDimensionTask(_dimension);
        }
        return super.onTick(mod);
    }

    @Override
    protected void onStart(Vibrato mod) {
        if (_preferStairs) {
            mod.getBehaviour().push();
            mod.getBehaviour().setPreferredStairs(true);
        }
        super.onStart(mod);
    }


    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        if (_preferStairs) {
            mod.getBehaviour().pop();
        }
        super.onStop(mod, interruptTask);
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof GetToBlockTask task) {
            return task._position.equals(_position) && task._preferStairs == _preferStairs && task._dimension == _dimension;
        }
        return false;
    }

    @Override
    public boolean isChurnGuardEligible() {
        return true;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return super.isFinished(mod) && (_dimension == null || _dimension == WorldHelper.getCurrentDimension());
    }

    @Override
    protected String toDebugString() {
        return "Getting to block " + _position + (_dimension != null ? " in dimension " + _dimension : "");
    }


    @Override
    protected Goal newGoal(Vibrato mod) {
        return new GoalBlock(_position);
    }

    @Override
    protected void onWander(Vibrato mod) {
        mod.getBlockTracker().requestBlockUnreachable(_position);
        super.onWander(mod);
    }
}
