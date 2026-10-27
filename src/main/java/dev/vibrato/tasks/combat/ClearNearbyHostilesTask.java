package dev.vibrato.tasks.combat;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.entity.KillEntityTask;
import dev.vibrato.tasksystem.Task;
import net.minecraft.entity.Entity;

import java.util.Optional;

/** Kills angry hostiles within a radius, one at a time, and finishes when none are left in range. */
public class ClearNearbyHostilesTask extends Task {

    private final double _radius;

    public ClearNearbyHostilesTask(double radius) {
        _radius = radius;
    }

    @Override
    protected void onStart(Vibrato mod) {

    }

    @Override
    protected Task onTick(Vibrato mod) {
        Optional<Entity> hostile = CombatTargets.closestHostile(mod, _radius);
        if (hostile.isEmpty()) {
            setDebugState("Area clear");
            return null;
        }
        setDebugState("Clearing hostiles");
        return new KillEntityTask(hostile.get());
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return CombatTargets.closestHostile(mod, _radius).isEmpty();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ClearNearbyHostilesTask task && task._radius == _radius;
    }

    @Override
    protected String toDebugString() {
        return "Clear hostiles within " + _radius + " blocks";
    }
}
