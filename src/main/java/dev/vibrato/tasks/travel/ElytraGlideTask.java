package dev.vibrato.tasks.travel;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.misc.EquipArmorTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.helpers.WorldHelper;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Glides towards a target with an equipped elytra: equips it, jumps off the ground, deploys the wings with a second jump,
 * and steers with a simple pitch rule. It boosts with a firework rocket when one is carried and the bot is slowing down,
 * and it flares to land near the target.
 * <p>
 * Limits, kept deliberately conservative: no obstacle avoidance, so it needs open sky along the way; the pitch rule is a
 * fixed altitude band rather than a physics model; it gives up if the wings do not deploy or the bot falls without them.
 * Takeoff and landing are unverified in game.
 */
public class ElytraGlideTask extends Task {

    private static final int MAX_TICKS = 20 * 60 * 6;
    private static final int DEPLOY_TRIES = 6;
    private static final int DEPLOY_INTERVAL_TICKS = 5;
    private static final int AIR_TICKS_BEFORE_GIVING_UP = 60;
    private static final int BOOST_INTERVAL_TICKS = 50;
    private static final double BOOST_SPEED = 0.9;
    private static final double LANDING_RANGE = 12;
    private static final double CRUISE_ALTITUDE = 40;
    private static final double LOW_ALTITUDE = 15;

    private final BlockPos _target;
    private int _ticks;
    private int _airTicks;
    private int _deployTries;
    private int _lastBoostTick;
    private boolean _gliding;
    private boolean _failed;

    public ElytraGlideTask(BlockPos target) {
        _target = target;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _ticks = 0;
        _airTicks = 0;
        _deployTries = 0;
        _lastBoostTick = -BOOST_INTERVAL_TICKS;
        _gliding = false;
        _failed = false;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        _ticks++;
        if (_ticks > MAX_TICKS) {
            _failed = true;
        }
        if (!StorageHelper.isEquipped(mod, Items.ELYTRA)) {
            if (!mod.getItemStorage().hasItem(Items.ELYTRA)) {
                _failed = true;
                return null;
            }
            setDebugState("Equipping elytra");
            return new EquipArmorTask(Items.ELYTRA);
        }
        if (_failed) {
            return null;
        }

        if (mod.getPlayer().isOnGround()) {
            if (_gliding) {
                return null;
            }
            setDebugState("Jumping off to start the glide");
            mod.getInputControls().tryPress(Input.JUMP);
            return null;
        }

        if (!mod.getPlayer().isFallFlying()) {
            _airTicks++;
            if (_airTicks > AIR_TICKS_BEFORE_GIVING_UP || _deployTries >= DEPLOY_TRIES) {
                setDebugState("Wings did not deploy");
                _failed = true;
                return null;
            }
            if (_airTicks % DEPLOY_INTERVAL_TICKS == 0) {
                _deployTries++;
                mod.getInputControls().tryPress(Input.JUMP);
            }
            setDebugState("Deploying wings");
            return null;
        }

        _gliding = true;
        glide(mod);
        return null;
    }

    private void glide(Vibrato mod) {
        double dx = _target.getX() + 0.5 - mod.getPlayer().getX();
        double dz = _target.getZ() + 0.5 - mod.getPlayer().getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        int ground = WorldHelper.getGroundHeight(mod, _target.getX(), _target.getZ());
        double altitude = mod.getPlayer().getY() - ground;

        float pitch;
        if (horizontal < LANDING_RANGE) {
            setDebugState("Landing near the target");
            pitch = altitude > 6 ? 5 : -35;
        } else if (altitude > CRUISE_ALTITUDE) {
            setDebugState("Gliding down to cruise height");
            pitch = 15;
        } else if (altitude < LOW_ALTITUDE) {
            setDebugState("Gliding low, levelling out");
            pitch = -10;
        } else {
            setDebugState("Gliding toward " + _target.toShortString());
            pitch = 0;
        }
        float yaw = TravelSteering.bearingDegrees(dx, dz);
        mod.getClientBaritone().getLookBehavior().updateTarget(new Rotation(MathHelper.wrapDegrees(yaw), pitch), false);

        boolean slowing = mod.getPlayer().getVelocity().length() < BOOST_SPEED;
        if (slowing && altitude > LOW_ALTITUDE && horizontal > LANDING_RANGE
                && _ticks - _lastBoostTick >= BOOST_INTERVAL_TICKS && mod.getItemStorage().hasItem(Items.FIREWORK_ROCKET)) {
            _lastBoostTick = _ticks;
            if (mod.getSlotHandler().forceEquipItem(Items.FIREWORK_ROCKET)) {
                mod.getController().interactItem(mod.getPlayer(), Hand.MAIN_HAND);
            }
        }
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        TravelSteering.releaseAll(mod, Input.JUMP);
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        if (_failed) {
            return true;
        }
        return _gliding && mod.getPlayer().isOnGround();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ElytraGlideTask task && task._target.equals(_target);
    }

    @Override
    protected String toDebugString() {
        return "Elytra glide toward " + _target.toShortString();
    }
}
