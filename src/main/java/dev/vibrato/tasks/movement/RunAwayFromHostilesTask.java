package dev.vibrato.tasks.movement;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.BaritoneHelper;
import dev.vibrato.util.baritone.GoalRunAwayFromEntities;
import baritone.api.pathing.goals.Goal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.SkeletonEntity;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RunAwayFromHostilesTask extends CustomBaritoneGoalTask {

    private final double _distanceToRun;
    private final boolean _includeSkeletons;

    public RunAwayFromHostilesTask(double distance, boolean includeSkeletons) {
        _distanceToRun = distance;
        _includeSkeletons = includeSkeletons;
    }

    public RunAwayFromHostilesTask(double distance) {
        this(distance, false);
    }


    @Override
    protected Goal newGoal(Vibrato mod) {
        // We want to run away NOW
        mod.getClientBaritone().getPathingBehavior().forceCancel();
        return new GoalRunAwayFromHostiles(mod, _distanceToRun);
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof RunAwayFromHostilesTask task) {
            return Math.abs(task._distanceToRun - _distanceToRun) < 1;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "NIGERUNDAYOO, SUMOOKEYY!";
    }

    private class GoalRunAwayFromHostiles extends GoalRunAwayFromEntities {

        public GoalRunAwayFromHostiles(Vibrato mod, double distance) {
            super(mod, distance, false, 0.8);
        }

        @Override
        protected List<Entity> getEntities(Vibrato mod) {
            List<Entity> result;
            Stream<Entity> stream = mod.getEntityTracker().getHostiles().stream();
            synchronized (BaritoneHelper.MINECRAFT_LOCK) {
                if (!_includeSkeletons) {
                    stream = stream.filter(hostile -> !(hostile instanceof SkeletonEntity));
                }
                return stream.collect(Collectors.toList());
            }
        }
    }
}
