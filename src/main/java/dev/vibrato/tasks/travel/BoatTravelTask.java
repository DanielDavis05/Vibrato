package dev.vibrato.tasks.travel;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.movement.GetToEntityTask;
import dev.vibrato.tasks.movement.GetToXZTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.ItemHelper;
import dev.vibrato.util.helpers.WorldHelper;
import baritone.api.utils.input.Input;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * Crosses open water by boat: crafts or reuses a boat, places it on the nearest water surface, boards it, and steers.
 * <p>
 * Steering works the way the game does it: a boat turns with the left and right keys, not with the camera. The camera is
 * still aimed at the target, smoothly. The task stops at the shore once within range, or when the boat has been on land
 * for a while, and it breaks a stranded boat so it can be placed again.
 * <p>
 * Limits: no obstacle handling on the water, no ice, and a fixed tick cap. A failed crossing ends at the cap.
 */
public class BoatTravelTask extends Task {

    private static final int MAX_TICKS = 20 * 60 * 8;
    private static final double ARRIVAL_RANGE = 8;
    private static final double SHORE_ARRIVAL_RANGE = 20;
    private static final int LAND_TICKS_BEFORE_RECOVERY = 40;
    private static final int SURFACE_SEARCH_RADIUS = 6;
    private static final double REACH_SQUARED = 9;
    private static final int PLACE_COOLDOWN_TICKS = 10;
    private static final float STEER_DEADZONE = 6;
    private static final float FORWARD_CONE = 75;

    private final BlockPos _target;
    private int _ticks;
    private int _landedTicks;
    private int _placeCooldown;
    private boolean _arrived;

    public BoatTravelTask(BlockPos target) {
        _target = target;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _ticks = 0;
        _landedTicks = 0;
        _placeCooldown = 0;
        _arrived = false;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        _ticks++;
        if (_ticks > MAX_TICKS) {
            _arrived = true;
        }
        if (mod.getPlayer().getVehicle() instanceof BoatEntity boat) {
            return sail(mod, boat);
        }
        TravelSteering.releaseAll(mod, Input.MOVE_FORWARD, Input.MOVE_LEFT, Input.MOVE_RIGHT, Input.SNEAK);
        if (_arrived) {
            return null;
        }
        _landedTicks = 0;
        return fetchBoat(mod);
    }

    private Task sail(Vibrato mod, BoatEntity boat) {
        Vec3d pos = boat.getPos();
        double dx = _target.getX() + 0.5 - pos.x;
        double dz = _target.getZ() + 0.5 - pos.z;
        double distance = Math.sqrt(dx * dx + dz * dz);

        _landedTicks = boat.isTouchingWater() ? 0 : _landedTicks + 1;
        if (distance < ARRIVAL_RANGE || (_landedTicks > LAND_TICKS_BEFORE_RECOVERY && distance < SHORE_ARRIVAL_RANGE)) {
            _arrived = true;
        }
        if (_arrived) {
            setDebugState("Stepping ashore");
            TravelSteering.releaseAll(mod, Input.MOVE_FORWARD, Input.MOVE_LEFT, Input.MOVE_RIGHT);
            mod.getInputControls().hold(Input.SNEAK);
            return null;
        }
        if (_landedTicks > LAND_TICKS_BEFORE_RECOVERY) {
            setDebugState("Boat hit land, getting out to recover it");
            TravelSteering.releaseAll(mod, Input.MOVE_FORWARD, Input.MOVE_LEFT, Input.MOVE_RIGHT);
            mod.getInputControls().hold(Input.SNEAK);
            return null;
        }

        setDebugState("Sailing toward " + _target.toShortString());
        float diff = MathHelper.wrapDegrees(TravelSteering.bearingDegrees(dx, dz) - boat.getYaw());
        if (Math.abs(diff) < FORWARD_CONE) {
            mod.getInputControls().hold(Input.MOVE_FORWARD);
        } else {
            mod.getInputControls().release(Input.MOVE_FORWARD);
        }
        if (diff > STEER_DEADZONE) {
            mod.getInputControls().hold(Input.MOVE_RIGHT);
            mod.getInputControls().release(Input.MOVE_LEFT);
        } else if (diff < -STEER_DEADZONE) {
            mod.getInputControls().hold(Input.MOVE_LEFT);
            mod.getInputControls().release(Input.MOVE_RIGHT);
        } else {
            TravelSteering.releaseAll(mod, Input.MOVE_LEFT, Input.MOVE_RIGHT);
        }
        TravelSteering.lookAt(mod, new Vec3d(_target.getX() + 0.5, pos.y, _target.getZ() + 0.5));
        return null;
    }

    private Task fetchBoat(Vibrato mod) {
        if (mod.getEntityTracker().getClosestEntity(BoatEntity.class).orElse(null) instanceof BoatEntity boat) {
            if (boat.squaredDistanceTo(mod.getPlayer()) > REACH_SQUARED) {
                setDebugState("Walking to a boat");
                return new GetToEntityTask(boat, 2);
            }
            TravelSteering.lookAt(mod, boat.getPos());
            if (boat.isTouchingWater()) {
                setDebugState("Boarding boat");
                mod.getController().interactEntity(mod.getPlayer(), boat, Hand.MAIN_HAND);
            } else {
                setDebugState("Breaking stranded boat so it can be placed again");
                mod.getController().attackEntity(mod.getPlayer(), boat);
            }
            return null;
        }

        Item boatItem = heldBoatItem(mod);
        if (boatItem == null) {
            setDebugState("Getting a boat");
            return TaskCatalogue.getItemTask("boat", 1);
        }

        BlockPos surface = nearestWaterSurface(mod).orElse(null);
        if (surface == null) {
            setDebugState("Walking towards the water");
            return new GetToXZTask(_target.getX(), _target.getZ());
        }
        if (mod.getPlayer().getBlockPos().getSquaredDistance(surface) > REACH_SQUARED) {
            setDebugState("Walking to the water's edge");
            return new GetToXZTask(surface.getX(), surface.getZ());
        }
        setDebugState("Placing boat");
        TravelSteering.lookAt(mod, Vec3d.ofCenter(surface));
        if (_placeCooldown > 0) {
            _placeCooldown--;
            return null;
        }
        _placeCooldown = PLACE_COOLDOWN_TICKS;
        if (mod.getSlotHandler().forceEquipItem(boatItem)) {
            mod.getController().interactItem(mod.getPlayer(), Hand.MAIN_HAND);
        }
        return null;
    }

    private static Item heldBoatItem(Vibrato mod) {
        for (Item boat : ItemHelper.WOOD_BOAT) {
            if (mod.getItemStorage().hasItem(boat)) {
                return boat;
            }
        }
        return null;
    }

    private static Optional<BlockPos> nearestWaterSurface(Vibrato mod) {
        BlockPos center = mod.getPlayer().getBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos start = center.add(-SURFACE_SEARCH_RADIUS, -2, -SURFACE_SEARCH_RADIUS);
        BlockPos end = center.add(SURFACE_SEARCH_RADIUS, 1, SURFACE_SEARCH_RADIUS);
        for (BlockPos pos : WorldHelper.scanRegion(mod, start, end)) {
            if (!mod.getWorld().getFluidState(pos).isOf(Fluids.WATER)) {
                continue;
            }
            if (!mod.getWorld().getBlockState(pos.up()).isAir()) {
                continue;
            }
            double distance = pos.getSquaredDistance(center);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.toImmutable();
            }
        }
        return Optional.ofNullable(best);
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        TravelSteering.releaseAll(mod, Input.MOVE_FORWARD, Input.MOVE_LEFT, Input.MOVE_RIGHT, Input.SNEAK);
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        if (mod.getPlayer().getVehicle() != null) {
            return false;
        }
        return _arrived || WorldHelper.inRangeXZ(mod.getPlayer(), _target, ARRIVAL_RANGE);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof BoatTravelTask task && task._target.equals(_target);
    }

    @Override
    protected String toDebugString() {
        return "Boat travel to " + _target.toShortString();
    }
}
