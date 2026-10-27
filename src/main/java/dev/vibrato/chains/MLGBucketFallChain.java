package dev.vibrato.chains;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.movement.MLGBucketTask;
import dev.vibrato.tasksystem.ITaskOverridesGrounded;
import dev.vibrato.tasksystem.TaskRunner;
import dev.vibrato.util.time.TimerGame;
import dev.vibrato.util.helpers.LookHelper;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.RaycastContext;

import java.util.Optional;

@SuppressWarnings("UnnecessaryLocalVariable")
public class MLGBucketFallChain extends SingleTaskChain implements ITaskOverridesGrounded {

    private final TimerGame _tryCollectWaterTimer = new TimerGame(4);
    private final TimerGame _pickupRepeatTimer = new TimerGame(0.25);
    private MLGBucketTask _lastMLG = null;
    private boolean _wasPickingUp = false;
    private boolean _doingChorusFruit = false;
    private boolean _fallEpisode;
    private double _fallStartY;
    private float _fallStartHealth;

    public MLGBucketFallChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    protected void onTaskFinish(Vibrato mod) {
        //_lastMLG = null;
    }

    @Override
    public float getPriority(Vibrato mod) {
        if (!Vibrato.inGame()) {
            if (_fallEpisode) {
                Debug.logInternal("Fall outcome=disconnected startY=%.2f (estimated)", _fallStartY);
                _fallEpisode = false;
            }
            return Float.NEGATIVE_INFINITY;
        }

        if (_lastMLG != null) _lastMLG.observeWaterPlacement(mod);
        if (_fallEpisode && (mod.getPlayer().isDead() || mod.getPlayer().isOnGround()
                || mod.getPlayer().isTouchingWater() || mod.getPlayer().isSwimming()
                || mod.getPlayer().isClimbing())) {
            String outcome = mod.getPlayer().isDead() ? "dead"
                    : mod.getPlayer().isTouchingWater() || mod.getPlayer().isSwimming() ? "water"
                    : mod.getPlayer().isClimbing() ? "climbing" : "ground";
            finishFallEpisode(mod, outcome);
        }

        if (!mod.getPlayer().isDead() && isFallingOhNo(mod)) {
            _tryCollectWaterTimer.reset();
            setTask(new MLGBucketTask());
            _lastMLG = (MLGBucketTask) _mainTask;
            if (!_fallEpisode) {
                _fallEpisode = true;
                _fallStartY = mod.getPlayer().getY() + mod.getPlayer().fallDistance;
                _fallStartHealth = mod.getPlayer().getHealth();
                _lastMLG.beginFallEpisode();
            }
            return 100;
        } else if (!_tryCollectWaterTimer.elapsed() && mod.getPlayer().getVelocity().y >= -0.5) { // Why -0.5? Cause it's slower than -0.7.
            // We just placed water, try to collect it.
            if (mod.getItemStorage().hasItem(Items.BUCKET) && !mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {

                if (_lastMLG != null) {
                    BlockPos placed = _lastMLG.getWaterPlacedPos();
                    //Debug.logInternal("PLACED: " + placed);
                    if (placed != null && placed.isWithinDistance(mod.getPlayer().getPos(), 5.5)) {
                        BlockPos toInteract = placed;
                        // Allow looking at fluids
                        mod.getBehaviour().push();
                        mod.getBehaviour().setRayTracingFluidHandling(RaycastContext.FluidHandling.SOURCE_ONLY);
                        Optional<Rotation> reach = LookHelper.getReach(toInteract, Direction.UP);
                        if (reach.isPresent()) {
                            mod.getClientBaritone().getLookBehavior().updateTarget(reach.get(), true);
                            if (mod.getClientBaritone().getPlayerContext().isLookingAt(toInteract)) {
                                if (mod.getSlotHandler().forceEquipItem(Items.BUCKET)) {
                                    if (_pickupRepeatTimer.elapsed()) {
                                        // Pick up
                                        _pickupRepeatTimer.reset();
                                        mod.getInputControls().tryPress(Input.CLICK_RIGHT);
                                        _wasPickingUp = true;
                                    } else if (_wasPickingUp) {
                                        // Stop picking up, wait and try again.
                                        _wasPickingUp = false;
                                    }
                                }
                            }
                        } else {
                            // Eh just try collecting water the regular way if all else fails.
                            setTask(TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1));
                        }
                        mod.getBehaviour().pop();
                        return 60;
                    }
                }
            }
        }
        if (_wasPickingUp) {
            _wasPickingUp = false;
            if (!_fallEpisode) _lastMLG = null;
        }
        if (mod.getPlayer().hasStatusEffect(StatusEffects.LEVITATION) &&
                !mod.getPlayer().getItemCooldownManager().isCoolingDown(Items.CHORUS_FRUIT) &&
                mod.getPlayer().getActiveStatusEffects().get(StatusEffects.LEVITATION).getDuration() <= 70 &&
                mod.getItemStorage().hasItemInventoryOnly(Items.CHORUS_FRUIT) &&
                !mod.getItemStorage().hasItemInventoryOnly(Items.WATER_BUCKET)) {
            _doingChorusFruit = true;
            mod.getSlotHandler().forceEquipItem(Items.CHORUS_FRUIT);
            mod.getInputControls().hold(Input.CLICK_RIGHT);
            mod.getExtraBaritoneSettings().setInteractionPaused(true);
        } else if (_doingChorusFruit) {
            _doingChorusFruit = false;
            mod.getInputControls().release(Input.CLICK_RIGHT);
            mod.getExtraBaritoneSettings().setInteractionPaused(false);
        }
        return Float.NEGATIVE_INFINITY;
    }

    private void finishFallEpisode(Vibrato mod, String outcome) {
        double endY = mod.getPlayer().getY();
        Debug.logInternal("Fall outcome=%s startY=%.2f (estimated) endY=%.2f estimatedHeight=%.2f health=%.1f->%.1f waterAttempted=%s otherAttempted=%s waterObserved=%s",
                outcome, _fallStartY, endY, Math.max(0, _fallStartY - endY), _fallStartHealth,
                mod.getPlayer().getHealth(), _lastMLG != null && _lastMLG.attemptedWater(),
                _lastMLG != null && _lastMLG.attemptedOther(),
                _lastMLG != null && _lastMLG.getWaterPlacedPos() != null);
        _fallEpisode = false;
    }

    @Override
    protected void onStop(Vibrato mod) {
        if (_fallEpisode) {
            if (Vibrato.inGame()) {
                if (_lastMLG != null) _lastMLG.observeWaterPlacement(mod);
                finishFallEpisode(mod, mod.getPlayer().isDead() ? "dead" : "stopped");
            } else {
                Debug.logInternal("Fall outcome=disconnected startY=%.2f (estimated)", _fallStartY);
                _fallEpisode = false;
            }
        }
        super.onStop(mod);
    }

    @Override
    public String getName() {
        return "MLG Water Bucket Fall Chain";
    }

    @Override
    public boolean isActive() {
        // We're always checking for mlg.
        return true;
    }

    public boolean isChorusFruiting() {
        return _doingChorusFruit;
    }

    public boolean isFallingOhNo(Vibrato mod) {
        if (!mod.getModSettings().shouldAutoMLGBucket()) {
            return false;
        }
        if (mod.getPlayer().isSwimming() || mod.getPlayer().isTouchingWater() || mod.getPlayer().isOnGround() || mod.getPlayer().isClimbing()) {
            // We're grounded.
            return false;
        }
        double ySpeed = mod.getPlayer().getVelocity().y;
        return ySpeed < -0.7;
    }
}
