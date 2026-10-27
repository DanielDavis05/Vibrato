package dev.vibrato.tasks.resources;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.ResourceTask;
import dev.vibrato.tasks.entity.KillEntitiesTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import net.minecraft.entity.Entity;

import java.util.function.Predicate;

public class KillAndLootTask extends ResourceTask {

    private final Class _toKill;

    private final Task _killTask;

    public KillAndLootTask(Class toKill, Predicate<Entity> shouldKill, ItemTarget... itemTargets) {
        super(itemTargets.clone());
        _toKill = toKill;
        _killTask = new KillEntitiesTask(shouldKill, _toKill);
    }

    public KillAndLootTask(Class toKill, ItemTarget... itemTargets) {
        super(itemTargets.clone());
        _toKill = toKill;
        _killTask = new KillEntitiesTask(_toKill);
    }

    @Override
    protected boolean shouldAvoidPickingUp(Vibrato mod) {
        return false;
    }

    @Override
    protected void onResourceStart(Vibrato mod) {

    }

    @Override
    protected Task onResourceTick(Vibrato mod) {
        if (!mod.getEntityTracker().entityFound(_toKill)) {
            if (isInWrongDimension(mod)) {
                setDebugState("Going to correct dimension.");
                return getToCorrectDimensionTask(mod);
            }
            setDebugState("Searching for mob...");
            return new TimeoutWanderTask(9999999);
        }
        // We found the mob!
        return _killTask;
    }

    @Override
    protected void onResourceStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        if (other instanceof KillAndLootTask task) {
            return task._toKill.equals(_toKill);
        }
        return false;
    }

    @Override
    protected String toDebugStringName() {
        return "Collect items from " + _toKill.toGenericString();
    }
}
