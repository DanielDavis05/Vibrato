package dev.vibrato.tasks.travel;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.movement.GetToBlockTask;
import dev.vibrato.tasks.movement.GetToEntityTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import baritone.api.utils.input.Input;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * Rides a minecart that sits on existing rail (mineshafts, villages, or rail a bot already passed). Crafts a cart when one
 * is missing, places it on the nearest rail, boards it, and rides until the cart stops or the target is reached.
 * <p>
 * Limits: this task does not lay rail. A cart on flat rail does not move by itself: it needs a downhill slope or powered
 * rail, and walking into a cart only pushes it. So on flat ground the ride ends quickly when the cart stalls. The stall
 * check dismounts the bot, and the tick cap bounds the whole task.
 */
public class MinecartRideTask extends Task {

    private static final int MAX_TICKS = 20 * 60 * 6;
    private static final int STALL_TICKS = 60;
    private static final double ARRIVAL_RANGE = 8;
    private static final double REACH_SQUARED = 9;
    private static final double STALL_SPEED_SQUARED = 0.0004;

    private final BlockPos _target;
    private int _ticks;
    private int _stalledTicks;
    private boolean _arrived;
    private boolean _failed;

    public MinecartRideTask(BlockPos target) {
        _target = target;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _ticks = 0;
        _stalledTicks = 0;
        _arrived = false;
        _failed = false;
        mod.getBlockTracker().trackBlock(Blocks.RAIL);
    }

    @Override
    protected Task onTick(Vibrato mod) {
        _ticks++;
        if (_ticks > MAX_TICKS) {
            _arrived = true;
        }
        if (mod.getPlayer().getVehicle() instanceof MinecartEntity cart) {
            return ride(mod, cart);
        }
        TravelSteering.releaseAll(mod, Input.SNEAK);
        if (_arrived || _failed) {
            return null;
        }

        Entity cart = mod.getEntityTracker().getClosestEntity(MinecartEntity.class).orElse(null);
        if (cart != null) {
            if (cart.squaredDistanceTo(mod.getPlayer()) > REACH_SQUARED) {
                setDebugState("Walking to a minecart");
                return new GetToEntityTask(cart, 2);
            }
            setDebugState("Boarding minecart");
            TravelSteering.lookAt(mod, cart.getPos());
            mod.getController().interactEntity(mod.getPlayer(), cart, Hand.MAIN_HAND);
            return null;
        }

        if (!mod.getItemStorage().hasItem(Items.MINECART)) {
            setDebugState("Getting a minecart");
            return TaskCatalogue.getItemTask("minecart", 1);
        }
        Optional<BlockPos> rail = mod.getBlockTracker().getNearestTracking(Blocks.RAIL);
        if (rail.isEmpty()) {
            setDebugState("Looking for rail");
            return new TimeoutWanderTask(true);
        }
        BlockPos railPos = rail.get();
        if (mod.getPlayer().getBlockPos().getSquaredDistance(railPos) > REACH_SQUARED) {
            setDebugState("Walking to rail");
            return new GetToBlockTask(railPos);
        }
        setDebugState("Placing minecart on rail");
        TravelSteering.lookAt(mod, Vec3d.ofCenter(railPos));
        if (mod.getSlotHandler().forceEquipItem(Items.MINECART)) {
            mod.getController().interactItem(mod.getPlayer(), Hand.MAIN_HAND);
        }
        return null;
    }

    private Task ride(Vibrato mod, MinecartEntity cart) {
        Vec3d pos = cart.getPos();
        double dx = _target.getX() + 0.5 - pos.x;
        double dz = _target.getZ() + 0.5 - pos.z;
        if (Math.sqrt(dx * dx + dz * dz) < ARRIVAL_RANGE) {
            _arrived = true;
        }
        _stalledTicks = cart.getVelocity().horizontalLengthSquared() < STALL_SPEED_SQUARED ? _stalledTicks + 1 : 0;
        if (_stalledTicks > STALL_TICKS) {
            setDebugState("Cart stalled, getting off");
            _failed = true;
        }
        if (_arrived || _failed) {
            setDebugState("Dismounting");
            mod.getInputControls().hold(Input.SNEAK);
            return null;
        }
        setDebugState("Riding the minecart toward " + _target.toShortString());
        return null;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        TravelSteering.releaseAll(mod, Input.SNEAK);
        mod.getBlockTracker().stopTracking(Blocks.RAIL);
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return (_arrived || _failed) && mod.getPlayer().getVehicle() == null;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof MinecartRideTask task && task._target.equals(_target);
    }

    @Override
    protected String toDebugString() {
        return "Riding a minecart toward " + _target.toShortString();
    }
}
