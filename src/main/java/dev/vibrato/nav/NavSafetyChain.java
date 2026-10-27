package dev.vibrato.nav;

import baritone.api.utils.input.Input;
import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.control.InputControls;
import dev.vibrato.director.DirectorTask;
import dev.vibrato.director.StepAbandonable;
import dev.vibrato.tasks.construction.DestroyBlockTask;
import dev.vibrato.util.Dimension;
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
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

/**
 * Watches the bot's motion while a user task is pathing and steps in when it is stuck or drowning.
 * <p>
 * Detection runs in {@link #getPriority} every tick, as other chains do. A stuck episode takes over with a higher
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
    private static final int ESCAPE_MAX_TICKS = 8 * 60 * 20;
    private static final double RECENTER_DONE_DISTANCE = 0.1;
    private static final double RECENTER_DEAD_ZONE = 0.02;
    private static final double RECENTER_SNEAK_DISTANCE = 0.5;
    private static final float SWIM_PITCH = -20.0f;
    private static final int BANK_RADIUS = 3;
    private static final double FRONT_REACH = 0.8;
    private static final double PROGRESS_DISTANCE = 1.5;

    private final StuckDetector _detector = new StuckDetector(StuckConfig.defaults());
    private final UnstuckLadder _ladder = new UnstuckLadder();
    private final WasteGuard _waste = WasteGuard.defaults();
    private final ActionRateLimiter _rateLimit = new ActionRateLimiter();
    private final PerStepCap _backOffCap = new PerStepCap(2);

    private long _tick;
    private Task _watchedTask;
    private int _watchedSerial;
    private long _lastBreakTick = NavGate.NEVER;
    private final MiningProgressSignal _miningProgress = new MiningProgressSignal();
    private final MotionProgress _progress = new MotionProgress(PROGRESS_DISTANCE);

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
            _backOffCap.reset();
            _progress.reset();
        }

        // A path still being calculated is not movement yet, but standing still for it is not stuck either.
        boolean pathing = mod.getClientBaritone().getPathingBehavior().isPathing();
        boolean calculating = NavGate.waitingForPath(pathing,
                mod.getClientBaritone().getPathingBehavior().getInProgress().isPresent());
        boolean wantsToMove = mod.getUserTaskChain().isActive() && pathing;
        // Breaking, placing, an open container and a path being calculated are all work: never stuck, always progress.
        if (miningMadeProgress(mod)) {
            _lastBreakTick = _tick;
        }
        boolean containerOpen = MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?>;
        boolean productive = calculating || NavGate.withinBreakGrace(_tick, _lastBreakTick) || containerOpen
                || mod.getClientBaritone().getBuilderProcess().isActive();
        if (productive) {
            _waste.onProgress(_tick);
        }
        MotionSample sample = sample(mod.getPlayer(), wantsToMove, productive);
        StuckState state = _detector.update(sample);
        if (!wantsToMove) {
            _detector.reset();
        }
        trackProgress(sample, wantsToMove && _action == null);
        boolean suppressed = NavGate.suppressed(
                !mod.getMobDefenseChain().getTasks().isEmpty(),
                !mod.getFoodChain().getTasks().isEmpty(),
                !mod.getMLGBucketChain().getTasks().isEmpty(),
                MinecraftClient.getInstance().currentScreen instanceof DeathScreen,
                MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?>,
                task instanceof DirectorTask director && director.currentStepStandsStill());

        if (_action == null) {
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
            } else if (!directorEscaping(task) && shouldEscapeUnderground(mod)
                    && _rateLimit.tryAcquire(_tick)) {
                startAction(mod, UnstuckAction.ESCAPE_SURFACE, "underground", PRIORITY_ACTION);
            }
        }

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
        _detector.reset();
        _ladder.onProgress();
        _progress.reset();
        _miningProgress.reset();
        _lastBreakTick = NavGate.NEVER;
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
        MotionProgress.Result progress = _progress.observe(sample.x(), sample.y(), sample.z(), wantsToMove);
        if (progress != MotionProgress.Result.NONE) {
            _waste.onProgress(_tick);
        }
        if (progress == MotionProgress.Result.MOVED) {
            _ladder.onProgress();
        }
    }

    private void startAction(Vibrato mod, UnstuckAction action, String reason, float priority) {
        Debug.logMessage("Unstuck " + action + " (" + reason + ")");
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
            case BREAK_BLOCKING_BLOCK, PLACE_STEP_BLOCK, DIG_UP, DIG_DOWN -> tickDelegate(mod, DELEGATE_MAX_TICKS);
            case ESCAPE_SURFACE -> tickDelegate(mod, ESCAPE_MAX_TICKS);
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

    private boolean tickDelegate(Vibrato mod, int maxTicks) {
        if (_delegate == null || _actionTicks >= maxTicks) return true;
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
            case ESCAPE_SURFACE -> new EscapeUndergroundTask();
            default -> null;
        };
    }

    /** The nearest solid bank with dry standing space within one step of the swimmer. */
    private static BlockPos findBank(Vibrato mod) {
        BlockPos origin = mod.getPlayer().getBlockPos();
        BlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int dx = -BANK_RADIUS; dx <= BANK_RADIUS; dx++) {
            for (int dz = -BANK_RADIUS; dz <= BANK_RADIUS; dz++) {
                int distance = dx * dx + dz * dz;
                if (distance == 0 || distance >= bestDistance) continue;
                for (int dy = -2; dy <= 0; dy++) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    if (BankCandidate.usable(dy, WorldHelper.isSolid(mod, pos),
                            !WorldHelper.isSolid(mod, pos.up()), !WorldHelper.isSolid(mod, pos.up(2)),
                            mod.getWorld().getFluidState(pos.up()).isEmpty(),
                            mod.getWorld().getFluidState(pos.up(2)).isEmpty())) {
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

    /** Underground in the overworld, with no way out on its own: the escape rung handles it. */
    private boolean shouldEscapeUnderground(Vibrato mod) {
        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            return false;
        }
        boolean underground = !SurfaceAccess.isAtSurface(mod);
        boolean hasPickaxe = mod.getItemStorage().hasItem(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE,
                Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE);
        return EscapeTrigger.shouldEscape(underground, hasPickaxe, _waste.ticksSinceProgress(_tick));
    }

    /** The Director already runs its own escape step; do not run a second one on top. */
    private static boolean directorEscaping(Task task) {
        return task instanceof DirectorTask director && director.currentStepIs("escape_underground");
    }

    private boolean miningMadeProgress(Vibrato mod) {
        var controller = mod.getControllerExtras();
        BlockPos current = controller.getBreakingBlockPos();
        Long currentKey = current == null ? null : current.asLong();
        Long trackedKey = _miningProgress.trackedPosition();
        boolean currentIsAir = currentKey != null && mod.getWorld().getBlockState(BlockPos.fromLong(currentKey)).isAir();
        boolean trackedIsAir = trackedKey != null && mod.getWorld().getBlockState(BlockPos.fromLong(trackedKey)).isAir();
        return _miningProgress.observe(currentKey, controller.getBreakingBlockProgress(), currentIsAir, trackedIsAir);
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
