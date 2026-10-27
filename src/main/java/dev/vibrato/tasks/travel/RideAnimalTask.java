package dev.vibrato.tasks.travel;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.movement.GetToEntityTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.StorageHelper;
import baritone.api.utils.input.Input;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.DonkeyEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Finds a horse, donkey or pig, tames it by mounting it again and again, saddles it when a saddle is carried, and rides
 * it towards a target. Ends at the target or after a tick cap; dismounts with the sneak key on arrival.
 * <p>
 * Limits: taming is a random number of attempts, so the attempt cap can fail on an unlucky horse. Animals are only
 * searched for among loaded entities; with none nearby the task explores. Pigs need a saddle and nothing else is done
 * about the carrot on a stick.
 */
public class RideAnimalTask extends Task {

    private static final int MAX_TICKS = 20 * 60 * 6;
    private static final int MAX_TAME_ATTEMPTS = 60;
    private static final double ARRIVAL_RANGE = 6;
    private static final double REACH_SQUARED = 9;
    private static final float FORWARD_CONE = 45;

    private final String _animal;
    private final BlockPos _target;
    private int _ticks;
    private int _tameAttempts;
    private boolean _arrived;
    private boolean _failed;

    /** @param animal an id from {@link RideableAnimals}; unsupported ids fail softly at the first tick */
    public RideAnimalTask(String animal, BlockPos target) {
        _animal = animal;
        _target = target;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _ticks = 0;
        _tameAttempts = 0;
        _arrived = false;
        _failed = false;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        _ticks++;
        if (_ticks > MAX_TICKS) {
            _arrived = true;
        }
        Class<? extends Entity> type = trackedClass(_animal);
        if (type == null) {
            _failed = true;
            setDebugState("Unknown animal " + _animal);
            return null;
        }
        if (mod.getPlayer().getVehicle() != null && type.isInstance(mod.getPlayer().getVehicle())) {
            return ride(mod, mod.getPlayer().getVehicle());
        }
        TravelSteering.releaseAll(mod, Input.MOVE_FORWARD, Input.SNEAK);
        if (_arrived || _failed) {
            return null;
        }

        Entity animal = mod.getEntityTracker().getClosestEntity(type).orElse(null);
        if (animal == null) {
            setDebugState("Searching for a " + _animal);
            return new TimeoutWanderTask(true);
        }
        if (animal.squaredDistanceTo(mod.getPlayer()) > REACH_SQUARED) {
            setDebugState("Walking to the " + _animal);
            return new GetToEntityTask(animal, 2);
        }
        TravelSteering.lookAt(mod, animal.getPos());
        if (isTamed(animal) && needsSaddleToRide(animal) && !isSaddled(animal)) {
            if (!mod.getItemStorage().hasItem(Items.SADDLE)) {
                setDebugState("No saddle for the " + _animal);
                _failed = true;
                return null;
            }
            setDebugState("Saddling the " + _animal);
            if (mod.getSlotHandler().forceEquipItem(Items.SADDLE)) {
                mod.getController().interactEntity(mod.getPlayer(), animal, Hand.MAIN_HAND);
            }
            return null;
        }
        if (_tameAttempts >= MAX_TAME_ATTEMPTS) {
            setDebugState("Could not tame the " + _animal);
            _failed = true;
            return null;
        }
        _tameAttempts++;
        setDebugState("Mounting the " + _animal);
        mod.getController().interactEntity(mod.getPlayer(), animal, Hand.MAIN_HAND);
        return null;
    }

    private Task ride(Vibrato mod, Entity mount) {
        Vec3d pos = mount.getPos();
        double dx = _target.getX() + 0.5 - pos.x;
        double dz = _target.getZ() + 0.5 - pos.z;
        if (Math.sqrt(dx * dx + dz * dz) < ARRIVAL_RANGE) {
            _arrived = true;
        }
        if (_arrived) {
            setDebugState("Dismounting");
            TravelSteering.releaseAll(mod, Input.MOVE_FORWARD);
            mod.getInputControls().hold(Input.SNEAK);
            return null;
        }
        setDebugState("Riding the " + _animal + " toward " + _target.toShortString());
        // The mount follows the rider's look, so aim the camera and only walk forward once roughly facing the target.
        TravelSteering.lookAt(mod, new Vec3d(_target.getX() + 0.5, pos.y, _target.getZ() + 0.5));
        float diff = MathHelper.wrapDegrees(TravelSteering.bearingDegrees(dx, dz) - mod.getPlayer().getYaw());
        if (Math.abs(diff) < FORWARD_CONE) {
            mod.getInputControls().hold(Input.MOVE_FORWARD);
        } else {
            mod.getInputControls().release(Input.MOVE_FORWARD);
        }
        return null;
    }

    private static Class<? extends Entity> trackedClass(String animal) {
        return switch (animal) {
            case "horse" -> HorseEntity.class;
            case "donkey" -> DonkeyEntity.class;
            case "pig" -> PigEntity.class;
            default -> null;
        };
    }

    private static boolean needsSaddleToRide(Entity animal) {
        return animal instanceof PigEntity;
    }

    private static boolean isTamed(Entity animal) {
        return !(animal instanceof AbstractHorseEntity horse) || horse.isTame();
    }

    private static boolean isSaddled(Entity animal) {
        if (animal instanceof AbstractHorseEntity horse) {
            return horse.isSaddled();
        }
        if (animal instanceof PigEntity pig) {
            return pig.isSaddled();
        }
        return false;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        TravelSteering.releaseAll(mod, Input.MOVE_FORWARD, Input.SNEAK);
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return (_arrived || _failed) && mod.getPlayer().getVehicle() == null;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof RideAnimalTask task && task._animal.equals(_animal) && task._target.equals(_target);
    }

    @Override
    protected String toDebugString() {
        return "Riding a " + _animal + " to " + _target.toShortString();
    }
}
