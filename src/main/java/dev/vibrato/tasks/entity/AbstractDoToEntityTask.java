package dev.vibrato.tasks.entity;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;
import dev.vibrato.tasks.movement.GetToEntityTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.ITaskRequiresGrounded;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.LookHelper;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.progresscheck.MovementProgressChecker;
import baritone.api.pathing.goals.GoalRunAway;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.Optional;

/**
 * Interacts with an entity while maintaining distance.
 *
 * The interaction is abstract.
 */
public abstract class AbstractDoToEntityTask extends Task implements ITaskRequiresGrounded {
    protected final MovementProgressChecker _progress = new MovementProgressChecker(5, 0.1, 5, 0.001, 2);
    private final double _maintainDistance;
    private final double _combatGuardLowerRange;
    private final double _combatGuardLowerFieldRadius;
    private final TimeoutWanderTask _wanderTask = new TimeoutWanderTask(10);

    public AbstractDoToEntityTask(double maintainDistance, double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
        _maintainDistance = maintainDistance;
        _combatGuardLowerRange = combatGuardLowerRange;
        _combatGuardLowerFieldRadius = combatGuardLowerFieldRadius;
    }

    public AbstractDoToEntityTask(double maintainDistance) {
        this(maintainDistance, 0, Double.POSITIVE_INFINITY);
    }

    public AbstractDoToEntityTask(double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
        this(-1, combatGuardLowerRange, combatGuardLowerFieldRadius);
    }
    public AbstractDoToEntityTask() {
        this(-1);
    }

    @Override
    protected void onStart(Vibrato mod) {
        _wanderTask.resetWander();
        _progress.reset();
        StorageHelper.closeScreen(); // Kinda duct tape but it should be future proof ish
    }

    @Override
    protected Task onTick(Vibrato mod) {

        if (_wanderTask.isActive() && !_wanderTask.isFinished(mod)) {
            _progress.reset();
            setDebugState("Failed to get to target, wandering for a bit.");
            return _wanderTask;
        }

        Optional<Entity> checkEntity = getEntityTarget(mod);


        // Oof
        if (checkEntity.isEmpty()) {
            mod.getMobDefenseChain().resetTargetEntity();
            mod.getMobDefenseChain().resetForceField();
            return _wanderTask;
        } else {
            mod.getMobDefenseChain().setTargetEntity(checkEntity.get());
        }
        Entity entity = checkEntity.get();

        double playerReach = mod.getModSettings().getEntityReachRange();

        LookHelper.lookAt(mod, entity.getPos().add(0, entity.getHeight() / 2, 0));
        EntityHitResult result = LookHelper.raycast(mod.getPlayer(), entity, playerReach);
        boolean visible = LookHelper.cleanLineOfSight(mod.getPlayer(), entity.getPos().add(0, entity.getHeight() / 2, 0), playerReach);
        var direction = entity.getBoundingBox().getCenter().subtract(mod.getPlayer().getEyePos()).normalize();
        boolean aligned = mod.getPlayer().getRotationVec(1.0f).dotProduct(direction) > 0.98;

        double sqDist = entity.squaredDistanceTo(mod.getPlayer());

        if (shouldLowerCombatGuard(sqDist, _combatGuardLowerRange)) {
            mod.getMobDefenseChain().setForceFieldRange(_combatGuardLowerFieldRadius);
        } else {
            mod.getMobDefenseChain().resetForceField();
        }

        // If we don't specify a maintain distance, default to within 1 block of our reach.
        double maintainDistance = _maintainDistance >= 0? _maintainDistance : playerReach - 1;

        boolean tooClose = sqDist < maintainDistance * maintainDistance;

        // Step away if we're too close
        if (tooClose && !(this instanceof AbstractKillEntityTask)) {
            //setDebugState("Maintaining distance");
            if (!mod.getClientBaritone().getCustomGoalProcess().isActive()) {
                mod.getClientBaritone().getCustomGoalProcess().setGoalAndPath(new GoalRunAway(maintainDistance, entity.getBlockPos()));
            }
        }

        if (entity.squaredDistanceTo(mod.getPlayer()) < playerReach * playerReach && result != null && result.getType() == HitResult.Type.ENTITY && visible && aligned) {
            _progress.reset();
            return onEntityInteract(mod, entity);
        } else if (!tooClose) {
            setDebugState("Approaching target");

            if (!_progress.check(mod)) {
                Debug.logMessage("Failed to get to target, wandering.");
                return _wanderTask;
            }

            // Move to target

            return new GetToEntityTask(entity, maintainDistance);
        }

        return null;
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof AbstractDoToEntityTask task) {
            if (!doubleCheck(task._maintainDistance, _maintainDistance)) return false;
            if (!doubleCheck(task._combatGuardLowerFieldRadius, _combatGuardLowerFieldRadius)) return false;
            if (!doubleCheck(task._combatGuardLowerRange, _combatGuardLowerRange)) return false;
            return isSubEqual(task);
        }
        return false;
    }

    static boolean shouldLowerCombatGuard(double squaredDistance, double lowerRange) {
        return lowerRange >= 0 && squaredDistance < lowerRange * lowerRange;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean doubleCheck(double a, double b) {
        if (Double.isInfinite(a) || Double.isInfinite(b)) return a == b;
        return Math.abs(a - b) < 0.1;
    }

    protected abstract boolean isSubEqual(AbstractDoToEntityTask other);

    protected abstract Task onEntityInteract(Vibrato mod, Entity entity);

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        mod.getMobDefenseChain().setTargetEntity(null);
        mod.getMobDefenseChain().resetForceField();
    }

    protected abstract Optional<Entity> getEntityTarget(Vibrato mod);

}
