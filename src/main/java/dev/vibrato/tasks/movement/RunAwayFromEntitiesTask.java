package dev.vibrato.tasks.movement;

import dev.vibrato.Vibrato;
import dev.vibrato.util.baritone.GoalRunAwayFromEntities;
import baritone.api.pathing.goals.Goal;
import net.minecraft.entity.Entity;

import java.util.List;
import java.util.function.Supplier;

public abstract class RunAwayFromEntitiesTask extends CustomBaritoneGoalTask {

    private final Supplier<List<Entity>> _runAwaySupplier;

    private final double _distanceToRun;
    private final boolean _xz;
    // See GoalrunAwayFromEntities penalty value
    private final double _penalty;

    public RunAwayFromEntitiesTask(Supplier<List<Entity>> toRunAwayFrom, double distanceToRun, boolean xz, double penalty) {
        _runAwaySupplier = toRunAwayFrom;
        _distanceToRun = distanceToRun;
        _xz = xz;
        _penalty = penalty;
    }

    public RunAwayFromEntitiesTask(Supplier<List<Entity>> toRunAwayFrom, double distanceToRun, double penalty) {
        this(toRunAwayFrom, distanceToRun, false, penalty);
    }


    @Override
    protected Goal newGoal(Vibrato mod) {
        return new GoalRunAwayStuff(mod, _distanceToRun, _xz);
    }


    private class GoalRunAwayStuff extends GoalRunAwayFromEntities {

        public GoalRunAwayStuff(Vibrato mod, double distance, boolean xz) {
            super(mod, distance, xz, _penalty);
        }

        @Override
        protected List<net.minecraft.entity.Entity> getEntities(Vibrato mod) {
            return _runAwaySupplier.get();
        }
    }
}
