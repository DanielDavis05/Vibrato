package dev.vibrato.nav;

import baritone.api.utils.input.Input;
import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.control.InputControls;
import dev.vibrato.director.DirectorTask;
import dev.vibrato.director.StepAbandonable;
import dev.vibrato.tasks.construction.DestroyBlockTask;
import dev.vibrato.tasks.construction.PlaceBlockNearbyTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.tasksystem.TaskChain;
import dev.vibrato.tasksystem.TaskRunner;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Watches the bot's motion while a user task is pathing and steps in when it is stuck or drowning.
 * <p>
 * Detection runs in {@link #getPriority} every tick, as other chains do. Water overlays (swim forward, bank jump) are
 * applied there too, so they work without taking over from the user task. A stuck episode takes over with a higher
 * priority for the duration of one {@link UnstuckAction}.
 */
public class NavSafetyChain extends TaskChain {

    private static final float PRIORITY_ACTION = 85;
    private static final float PRIORITY_AIR = 95;

    private static final int SIDESTEP_TICKS = 6;
    private static final int BACK_OFF_TICKS = 10;
    private static final int RECENTER_MAX_TICKS = 40;
    private static final int CLIMB_MAX_TICKS = 60;
    private static final int SWIM_MAX_TICKS = 100;
    private static final int DELEGATE_MAX_TICKS = 200;
    private static final double RECENTER_DONE_DISTANCE = 0.1;
    private static final double RECENTER_DEAD_ZONE = 0.02;
    private static final double RECENTER_SNEAK_DISTANCE = 0.5;
    private static final float SWIM_PITCH = -20.0f;
    private static final int BANK_RADIUS = 3;
    private static final double FRONT_REACH = 0.8;
    private static final double HEAD_HEIGHT = 1.0;
    private static final double SWIM_TRAVEL_BLOCKS = 8.0;
    private static final double PROGRESS_DISTANCE = 1.5;

    private final StuckDetector _detector = new StuckDetector(StuckConfig.defaults());
    private final UnstuckLadder _ladder = new UnstuckLadder();
    private final WasteGuard _waste = WasteGuard.defaults();
    private final ActionRateLimiter _rateLimit = new ActionRateLimiter();
    private final PerStepCap _backOffCap = new PerStepCap(2);

    private long _tick;
    private Task _watchedTask;
    private int _watchedSerial;
    private boolean _anchored;
    private double _anchorX;
    private double _anchorZ;
    private double _prevX;
    private double _prevZ;
    private double _waterTravel;
    private boolean _overlayJump;
    private boolean _overlayForward;
    private boolean _overlaySprint;

    private UnstuckAction _action;
    private float _actionPriority;
    private String _actionReason;
    private int _actionTicks;
    private double _targetX;
    private double _targetZ;
    private BlockPos _bank;
    private Task _delegate;

    public NavSafetyChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    public float getPriority(Vibrato mod) {
        if (!Vibrato.inGame()) {
            finishAction(mod);
            return Float.NEGATIVE_INFINITY;
        }
        _tick++;

        Task task = mod.getUserTaskChain().getCurrentTask();
        int serial = task instanceof StepAbandonable steps ? steps.stepSerial() : 0;
        if (task != _watchedTask || serial != _watchedSerial) {
            _watchedTask = task;
            _watchedSerial = serial;
            _waste.start(_tick);
            _ladder.onProgress();
            _detector.reset();
        }

        // A path still being calculated is not movement yet, but standing still for it is not stuck either.
        boolean calculating = mod.getClientBaritone().getPathingBehavior().getInProgress().isPresent();
        boolean wantsToMove = mod.getUserTaskChain().isActive()
                && mod.getClientBaritone().getPathingBehavior().isPathing() && !calculating;
        boolean productive = calculating || isBreakingBlock() || mod.getClientBaritone().getBuilderProcess().isActive();
        MotionSample sample = sample(mod.getPlayer(), wantsToMove, productive);
        StuckState state = _detector.update(sample);
        if (!wantsToMove) {
            _detector.reset();
        }
        trackProgress(sample, wantsToMove);
        boolean suppressed = NavGate.suppressed(
                !mod.getMobDefenseChain().getTasks().isEmpty(),
                !mod.getFoodChain().getTasks().isEmpty(),
                !mod.getMLGBucketChain().getTasks().isEmpty(),
                MinecraftClient.getInstance().currentScreen instanceof DeathScreen,
                MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?>,
                task instanceof DirectorTask director && director.currentStepStandsStill());

        if (_action == null) {
            updateWaterOverlay(mod, sample);
            if (suppressed) {
                _detector.reset();
            } else if (wantsToMove && _waste.isWasting(_tick) && _rateLimit.tryAcquire(_tick)) {
                startAction(mod, UnstuckAction.ABANDON_TASK, "the step made no progress", PRIORITY_ACTION);
            } else if (state != StuckState.OK && _ladder.ready(_tick) && _rateLimit.tryAcquire(_tick)) {
                UnstuckAction action = _ladder.next(state);
                if (action == UnstuckAction.BACK_OFF_AND_REPATH && !_backOffCap.tryUse(_watchedSerial)) {
                    action = UnstuckAction.ABANDON_TASK;
                }
                _ladder.markFired(_tick);
                startAction(mod, action, state.name(), state == StuckState.LOW_AIR ? PRIORITY_AIR : PRIORITY_ACTION);
            }
        }

        _prevX = sample.x();
        _prevZ = sample.z();
        return _action == null ? 0 : _actionPriority;
    }

    @Override
    protected void onTick(Vibrato mod) {
        if (_action == null) return;
        _actionTicks++;
        if (tickAction(mod)) {
            finishAction(mod);
        }
    }

    @Override
    public void onInterrupt(Vibrato mod, TaskChain other) {
        finishAction(mod);
    }

    @Override
    protected void onStop(Vibrato mod) {
        finishAction(mod);
        setOverlay(mod, false, false, false);
        _detector.reset();
        _ladder.onProgress();
        _waterTravel = 0;
        _anchored = false;
        _watchedTask = null;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public String getName() {
        return "Never Stuck Navigation Chain";
    }

    private void trackProgress(MotionSample sample, boolean wantsToMove) {
        // Idle time is never waste, and moving a few blocks from the anchor counts as progress.
        if (!_anchored || !wantsToMove || Math.hypot(sample.x() - _anchorX, sample.z() - _anchorZ) >= PROGRESS_DISTANCE) {
            _anchored = true;
            _anchorX = sample.x();
            _anchorZ = sample.z();
            _ladder.onProgress();
            _waste.onProgress(_tick);
        }
    }

    private void updateWaterOverlay(Vibrato mod, MotionSample sample) {
        if (!sample.inWater() || !sample.wantsToMove()) {
            _waterTravel = 0;
            setOverlay(mod, false, false, false);
            return;
        }
        _waterTravel += Math.hypot(sample.x() - _prevX, sample.z() - _prevZ);

        boolean bank = sample.horizontalCollision()
                && WorldHelper.isSolid(mod, ahead(sample.x(), sample.y() + HEAD_HEIGHT, sample.z(), sample.yaw()));
        if (bank) {
            setOverlay(mod, true, true, false);
        } else {
            boolean swim = _waterTravel > SWIM_TRAVEL_BLOCKS;
            setOverlay(mod, false, swim, swim);
        }
    }

    private void setOverlay(Vibrato mod, boolean jump, boolean forward, boolean sprint) {
        InputControls input = mod.getInputControls();
        if (jump != _overlayJump) {
            setKey(input, Input.JUMP, jump);
            _overlayJump = jump;
        }
        if (forward != _overlayForward) {
            setKey(input, Input.MOVE_FORWARD, forward);
            _overlayForward = forward;
        }
        if (sprint != _overlaySprint) {
            setKey(input, Input.SPRINT, sprint);
            _overlaySprint = sprint;
        }
    }

    private void startAction(Vibrato mod, UnstuckAction action, String reason, float priority) {
        Debug.logMessage("Unstuck " + action + " (" + reason + ")");
        setOverlay(mod, false, false, false);
        if (action.cancelsPath()) {
            mod.getClientBaritone().getPathingBehavior().cancelEverything();
        }

        ClientPlayerEntity player = mod.getPlayer();
        _action = action;
        _actionPriority = priority;
        _actionReason = reason;
        _actionTicks = 0;
        _targetX = Math.floor(player.getX()) + 0.5;
        _targetZ = Math.floor(player.getZ()) + 0.5;
        _bank = action == UnstuckAction.CLIMB_OUT_OF_WATER ? findBank(mod) : null;
        _delegate = createDelegate(mod, action);
    }

    private void finishAction(Vibrato mod) {
        if (_action == null) return;
        if (_delegate != null) {
            _delegate.stop(mod);
            _delegate = null;
        }
        releaseMovement(mod);
        _action = null;
        _detector.reset();
    }

    /** Runs one tick of the current action. Returns true when it is finished. */
    private boolean tickAction(Vibrato mod) {
        InputControls input = mod.getInputControls();
        return switch (_action) {
            case JUMP -> {
                input.tryPress(Input.JUMP);
                yield true;
            }
            case SIDESTEP_LEFT -> {
                input.hold(Input.MOVE_LEFT);
                yield _actionTicks >= SIDESTEP_TICKS;
            }
            case SIDESTEP_RIGHT -> {
                input.hold(Input.MOVE_RIGHT);
                yield _actionTicks >= SIDESTEP_TICKS;
            }
            case BACK_OFF_AND_REPATH -> {
                input.hold(Input.MOVE_BACK);
                yield _actionTicks >= BACK_OFF_TICKS;
            }
            case RECENTER_ON_BLOCK -> recenter(mod);
            case SWIM_TO_SURFACE -> swimUp(mod);
            case CLIMB_OUT_OF_WATER -> climbOut(mod);
            case BREAK_BLOCKING_BLOCK, PLACE_STEP_BLOCK, DIG_UP, DIG_DOWN -> tickDelegate(mod);
            case ABANDON_TASK -> {
                Debug.logWarning("Abandoning " + _actionReason);
                if (mod.getUserTaskChain().getCurrentTask() instanceof StepAbandonable steps) {
                    steps.abandonCurrentStep(_actionReason);
                } else {
                    mod.getUserTaskChain().cancel(mod);
                }
                yield true;
            }
        };
    }

    /** Walks to the centre of the block under the bot, sneaking for the last half block. */
    private boolean recenter(Vibrato mod) {
        if (_actionTicks >= RECENTER_MAX_TICKS) return true;
        ClientPlayerEntity player = mod.getPlayer();
        double dx = _targetX - player.getX();
        double dz = _targetZ - player.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance < RECENTER_DONE_DISTANCE) return true;

        double radians = Math.toRadians(player.getYaw());
        double forward = -dx * Math.sin(radians) + dz * Math.cos(radians);
        double right = -dx * Math.cos(radians) - dz * Math.sin(radians);
        InputControls input = mod.getInputControls();
        setKey(input, Input.MOVE_FORWARD, forward > RECENTER_DEAD_ZONE);
        setKey(input, Input.MOVE_BACK, forward < -RECENTER_DEAD_ZONE);
        setKey(input, Input.MOVE_RIGHT, right > RECENTER_DEAD_ZONE);
        setKey(input, Input.MOVE_LEFT, right < -RECENTER_DEAD_ZONE);
        setKey(input, Input.SNEAK, distance < RECENTER_SNEAK_DISTANCE);
        return false;
    }

    private boolean swimUp(Vibrato mod) {
        ClientPlayerEntity player = mod.getPlayer();
        mod.getInputControls().hold(Input.JUMP);
        mod.getInputControls().forceLook(player.getYaw(), SWIM_PITCH);
        return !player.isSubmergedInWater() || _actionTicks >= SWIM_MAX_TICKS;
    }

    private boolean climbOut(Vibrato mod) {
        if (_bank == null || _actionTicks >= CLIMB_MAX_TICKS) return true;
        ClientPlayerEntity player = mod.getPlayer();
        if (_actionTicks > 1 && player.isOnGround() && !player.isTouchingWater()) return true;

        double dx = _bank.getX() + 0.5 - player.getX();
        double dz = _bank.getZ() + 0.5 - player.getZ();
        InputControls input = mod.getInputControls();
        input.forceLook((float) Math.toDegrees(Math.atan2(-dx, dz)), player.getPitch());
        input.hold(Input.MOVE_FORWARD);
        input.hold(Input.SPRINT);
        input.hold(Input.JUMP);
        return false;
    }

    private boolean tickDelegate(Vibrato mod) {
        if (_delegate == null || _actionTicks >= DELEGATE_MAX_TICKS) return true;
        _delegate.tick(mod, this);
        return _delegate.isFinished(mod) || _delegate.stopped();
    }

    private static Task createDelegate(Vibrato mod, UnstuckAction action) {
        ClientPlayerEntity player = mod.getPlayer();
        BlockPos feet = player.getBlockPos();
        BlockPos front = ahead(player.getX(), player.getY(), player.getZ(), player.getYaw());
        return switch (action) {
            case BREAK_BLOCKING_BLOCK -> {
                BlockPos target = WorldHelper.isSolid(mod, front) ? front : front.up();
                yield WorldHelper.isSolid(mod, target) ? new DestroyBlockTask(target) : null;
            }
            case DIG_UP -> WorldHelper.isSolid(mod, feet.up(2)) ? new DestroyBlockTask(feet.up(2)) : null;
            case DIG_DOWN -> WorldHelper.isSolid(mod, feet.down()) ? new DestroyBlockTask(feet.down()) : null;
            case PLACE_STEP_BLOCK -> {
                BlockPos step = front.down();
                yield new PlaceBlockNearbyTask(pos -> pos.equals(step), Blocks.DIRT, Blocks.COBBLESTONE);
            }
            default -> null;
        };
    }

    /** The nearest dry, solid block with air or water above it that a bot could climb onto. */
    private static BlockPos findBank(Vibrato mod) {
        BlockPos origin = mod.getPlayer().getBlockPos();
        BlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int dx = -BANK_RADIUS; dx <= BANK_RADIUS; dx++) {
            for (int dz = -BANK_RADIUS; dz <= BANK_RADIUS; dz++) {
                int distance = dx * dx + dz * dz;
                if (distance == 0 || distance >= bestDistance) continue;
                for (int dy = -2; dy <= 1; dy++) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    if (WorldHelper.isSolid(mod, pos) && !WorldHelper.isSolid(mod, pos.up()) && !WorldHelper.isSolid(mod, pos.up(2))) {
                        best = pos;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private static BlockPos ahead(double x, double y, double z, float yaw) {
        double radians = Math.toRadians(yaw);
        return BlockPos.ofFloored(x - Math.sin(radians) * FRONT_REACH, y, z + Math.cos(radians) * FRONT_REACH);
    }

    private MotionSample sample(ClientPlayerEntity player, boolean wantsToMove, boolean productive) {
        return new MotionSample(_tick, player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch(),
                player.isOnGround(), player.isTouchingWater(), player.isSubmergedInWater(),
                player.horizontalCollision, wantsToMove, player.getAir(), productive);
    }

    private static boolean isBreakingBlock() {
        var interactions = MinecraftClient.getInstance().interactionManager;
        return interactions != null && interactions.isBreakingBlock();
    }

    private static void setKey(InputControls input, Input key, boolean pressed) {
        if (pressed) {
            input.hold(key);
        } else {
            input.release(key);
        }
    }

    private static void releaseMovement(Vibrato mod) {
        InputControls input = mod.getInputControls();
        input.release(Input.MOVE_FORWARD);
        input.release(Input.MOVE_BACK);
        input.release(Input.MOVE_LEFT);
        input.release(Input.MOVE_RIGHT);
        input.release(Input.JUMP);
        input.release(Input.SNEAK);
        input.release(Input.SPRINT);
    }
}
