package dev.vibrato.tasks.resources;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.ResourceTask;
import dev.vibrato.tasks.entity.KillEntitiesTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.ChickenEntity;

import java.util.function.Predicate;
import java.util.UUID;
import java.util.Objects;

public class KillAndLootTask extends ResourceTask {

    private final Class _toKill;
    private final UUID _specificTarget;

    private final Task _killTask;
    private final Task _landSearch = new LandAnimalSearchTask();

    public KillAndLootTask(Class toKill, Predicate<Entity> shouldKill, ItemTarget... itemTargets) {
        super(itemTargets.clone());
        _toKill = toKill;
        _specificTarget = null;
        _killTask = new KillEntitiesTask(shouldKill, _toKill);
    }

    public KillAndLootTask(Class toKill, ItemTarget... itemTargets) {
        super(itemTargets.clone());
        _toKill = toKill;
        _specificTarget = null;
        _killTask = new KillEntitiesTask(_toKill);
    }

    /** Pursue one specific animal while preserving ResourceTask drop pickup. */
    public KillAndLootTask(Class toKill, UUID target, ItemTarget... itemTargets) {
        super(itemTargets.clone());
        _toKill = toKill;
        _specificTarget = Objects.requireNonNull(target);
        _killTask = new KillEntitiesTask(entity -> matchesTarget(target, entity.getUuid()), _toKill);
    }

    static boolean sameTarget(Class<?> firstClass, UUID first, Class<?> secondClass, UUID second) {
        return firstClass.equals(secondClass) && Objects.equals(first, second);
    }

    static boolean matchesTarget(UUID target, UUID candidate) {
        return target.equals(candidate);
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
            if (_toKill == SheepEntity.class || _toKill == CowEntity.class
                    || _toKill == PigEntity.class || _toKill == ChickenEntity.class) {
                return _landSearch;
            }
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
            return sameTarget(_toKill, _specificTarget, task._toKill, task._specificTarget);
        }
        return false;
    }

    @Override
    protected String toDebugStringName() {
        return "Collect items from " + _toKill.toGenericString();
    }
}
