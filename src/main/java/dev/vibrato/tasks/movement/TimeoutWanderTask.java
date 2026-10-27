package dev.vibrato.tasks.movement;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;
import dev.vibrato.director.DirectorTask;
import dev.vibrato.tasks.ResourceTask;
import dev.vibrato.tasks.AbstractDoToClosestObjectTask;
import dev.vibrato.tasksystem.TaskChain;
import net.minecraft.client.MinecraftClient;
import dev.vibrato.tasksystem.ITaskRequiresGrounded;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.ItemHelper;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.progresscheck.MovementProgressChecker;
import dev.vibrato.util.time.TimerGame;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalRunAway;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Call this when the place you're currently at is bad for some reason and you just wanna get away.
 */
public class TimeoutWanderTask extends Task implements ITaskRequiresGrounded {

    private static final HashSet<Block> ANNOYING_STUCK_BLOCKS;
    static {
        ANNOYING_STUCK_BLOCKS = new HashSet<>();
        ANNOYING_STUCK_BLOCKS.addAll(Arrays.stream(ItemHelper.WOOD_FENCE).map(Block::getBlockFromItem).toList());
        //ANNOYING_STUCK_BLOCKS.addAll(Arrays.stream(ItemHelper.WOOD_DOOR).map(Block::getBlockFromItem).toList());
        ANNOYING_STUCK_BLOCKS.addAll(Arrays.stream(ItemHelper.FLOWER).map(Block::getBlockFromItem).toList());
        ANNOYING_STUCK_BLOCKS.addAll(Arrays.asList(Blocks.VINE,
                Blocks.NETHER_SPROUTS,
                Blocks.CAVE_VINES,
                Blocks.CAVE_VINES_PLANT,
                Blocks.TWISTING_VINES,
                Blocks.TWISTING_VINES_PLANT,
                Blocks.WEEPING_VINES_PLANT,
                Blocks.LADDER,
                Blocks.BIG_DRIPLEAF,
                Blocks.BIG_DRIPLEAF_STEM,
                Blocks.SMALL_DRIPLEAF,
                Blocks.TALL_GRASS,
                Blocks.SNOW,
                Blocks.SHORT_GRASS
        ));
    }

    /** Used by the no-argument constructor, so a wander always ends on its own. */
    public static final float DEFAULT_DISTANCE = 64;
    /** Hard cap on one wander, whatever its distance. */
    public static final double TIME_CAP_SECONDS = 120;

    private final float _distanceToWander;
    private final TimerGame _timeCap = new TimerGame(TIME_CAP_SECONDS);
    private final MovementProgressChecker _progressChecker = new MovementProgressChecker();
    private final boolean _increaseRange;
    //private DistanceProgressChecker _distanceProgressChecker = new DistanceProgressChecker(10, 0.1f);

    private final WanderPolicy.Attempt _attempt = new WanderPolicy.Attempt();
    private boolean _completedAtStop;
    private Vec3d _origin;
    private boolean _executingPlanB = false;
    private boolean _forceExplore;
    private Task _unstuckTask = null;
    private int _failCounter;
    private double _wanderDistanceExtension;
    private final SearchDetourBudget _detourBudget = new SearchDetourBudget();
    private SearchOpportunityTask _detour;
    private Vec3d _lastDetourPosition;

    public TimeoutWanderTask(float distanceToWander, boolean increaseRange) {
        _distanceToWander = distanceToWander;
        _increaseRange = increaseRange;
        _forceExplore = false;
    }

    public TimeoutWanderTask(float distanceToWander) {
        this(distanceToWander, false);
    }

    public TimeoutWanderTask() {
        this(DEFAULT_DISTANCE, false);
    }

    public TimeoutWanderTask(boolean forceExplore) {
        this();
        _forceExplore = forceExplore;
    }

    private static BlockPos[] generateSides(BlockPos pos) {
        return new BlockPos[]{
                pos.add(1, 0, 0),
                pos.add(-1, 0, 0),
                pos.add(0, 0, 1),
                pos.add(0, 0, -1),
        };
    }

    public void resetWander() {
        _executingPlanB = false;
        _wanderDistanceExtension = 0;
        _attempt.reset();
    }

    @Override
    protected void onStart(Vibrato mod) {
        if (_attempt.start(_completedAtStop)) {
            beginAttempt(mod);
        }
        _completedAtStop = false;
        // Pauses can involve combat movement; do not count them as failed wander progress.
        _progressChecker.reset();
        StorageHelper.closeScreen();
    }

    private void beginAttempt(Vibrato mod) {
        _timeCap.reset();
        _origin = mod.getPlayer().getPos();
        _failCounter = 0;
        _executingPlanB = false;
        _unstuckTask = null;
        _detour = null;
        _lastDetourPosition = null;
        _detourBudget.reset();
    }

    @Override
    protected Task onTick(Vibrato mod) {
        // Some parents explicitly reset a retained child while it is still active.
        if (_attempt.start(false)) {
            beginAttempt(mod);
            _progressChecker.reset();
        }

        if (_unstuckTask != null && _unstuckTask.isActive() && !_unstuckTask.isFinished(mod) && stuckInBlock(mod) != null) {
            setDebugState("Getting unstuck from block.");
            // Stop other tasks, we are JUST shimmying
            mod.getClientBaritone().getCustomGoalProcess().onLostControl();
            mod.getClientBaritone().getExploreProcess().onLostControl();
            return _unstuckTask;
        }

        if (_detour != null) {
            if (!safeForDetour(mod) || !_detourBudget.canContinue(_timeCap.getDuration()) || _detour.isFinished(mod)) {
                if (!_detour.canBeInterruptedBy(mod, null)) return _detour;
                endDetour(mod);
                // The child is stopped after this tick. Resume exploration on the next tick.
                return null;
            }
            setDebugState("Taking a short search opportunity.");
            return _detour;
        }
        double travelSquared = _lastDetourPosition == null ? Double.POSITIVE_INFINITY
                : mod.getPlayer().getPos().squaredDistanceTo(_lastDetourPosition);
        if (mod.getPlayer().age % 20 == 0 && isSearchContext(mod) && safeForDetour(mod)
                && _detourBudget.canStart(_timeCap.getDuration(), travelSquared)) {
            SearchOpportunityTask opportunity = SearchOpportunityTask.find(mod);
            if (opportunity != null && canBeInterruptedBy(mod, opportunity)) {
                _detour = opportunity;
                _detourBudget.start(_timeCap.getDuration());
                mod.getClientBaritone().getExploreProcess().onLostControl();
                mod.getClientBaritone().getCustomGoalProcess().onLostControl();
                mod.getClientBaritone().getPathingBehavior().cancelEverything();
                _progressChecker.reset();
                return _detour;
            }
        }

        if (_executingPlanB) {
            setDebugState("Plan B: Random direction.");
            if (!mod.getClientBaritone().getCustomGoalProcess().isActive()) {
                mod.getClientBaritone().getCustomGoalProcess().setGoalAndPath(getRandomDirectionGoal(mod));
            }
        } else {
            setDebugState("Exploring.");
            if (!mod.getClientBaritone().getExploreProcess().isActive()) {
                mod.getClientBaritone().getExploreProcess().explore((int) _origin.getX(), (int) _origin.getZ());
            }
        }

        //_distanceProgressChecker.setProgress(mod.getPlayer().getPos());
        //if (_distanceProgressChecker.failed()) {
        if (!_progressChecker.check(mod)) {
            // We failed at exploring.
            //_distanceProgressChecker.reset();
            _progressChecker.reset();

            BlockPos blockStuck = stuckInBlock(mod);
            if (blockStuck != null) {
                _failCounter++;
                Debug.logMessage("Failed exploring, found stuck block nearby.");
                _unstuckTask = getFenceUnstuckTask(mod, blockStuck);
                return _unstuckTask;
            }

            if (!_forceExplore) {
                _failCounter++;
                Debug.logMessage("Failed exploring.");
                if (_executingPlanB) {
                    // Cancel current plan B
                    mod.getClientBaritone().getCustomGoalProcess().onLostControl();
                }
                _executingPlanB = true;
            }
        }

        return null;
    }

    private void endDetour(Vibrato mod) {
        _detourBudget.end(_timeCap.getDuration());
        _lastDetourPosition = mod.getPlayer().getPos();
        _detour = null;
        _progressChecker.reset();
    }

    private boolean isSearchContext(Vibrato mod) {
        TaskChain chain = mod.getTaskRunner().getCurrentTaskChain();
        if (chain == null || chain.getTasks().size() < 2) return false;
        var tasks = chain.getTasks();
        Task root = tasks.get(0);
        Task parent = tasks.get(tasks.size() - 2);
        if (!(root instanceof DirectorTask) && !(root instanceof ResourceTask)) return false;
        return parent instanceof ResourceTask || parent instanceof DirectorTask
                || parent instanceof SearchChunksExploreTask
                || (parent instanceof AbstractDoToClosestObjectTask<?> && !(parent instanceof PickupDroppedItemTask));
    }

    private boolean safeForDetour(Vibrato mod) {
        var player = mod.getPlayer();
        if (MinecraftClient.getInstance().currentScreen != null || !StorageHelper.getItemStackInCursorSlot().isEmpty()
                || !player.isOnGround() || player.isTouchingWater() || player.isInLava() || player.isOnFire()
                || player.getHealth() < player.getMaxHealth() * 0.75
                || player.getVelocity().horizontalLengthSquared() > 0.02
                || (_detour == null && mod.getControllerExtras().isBreakingBlock())
                || mod.getClientBaritone().getBuilderProcess().isActive()) return false;
        if (mod.getEntityTracker().getHostiles().stream().anyMatch(entity -> entity.isAlive() && entity.isInRange(player, 10))) return false;
        BlockPos feet = player.getBlockPos();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockPos below = feet.add(x, -1, z);
            BlockPos standing = feet.add(x, 0, z);
            if (!mod.getWorld().getBlockState(below).isSolidBlock(mod.getWorld(), below)
                    || !mod.getWorld().getFluidState(standing).isEmpty()
                    || !mod.getWorld().getFluidState(standing.up()).isEmpty()) return false;
        }
        return true;
    }

    private Goal getRandomDirectionGoal(Vibrato mod) {
        double distance = Float.isInfinite(_distanceToWander) ? _distanceToWander : _distanceToWander + _wanderDistanceExtension;
        return new GoalRunAway(distance, mod.getPlayer().getBlockPos());
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        if (_detour != null && mod.getPlayer() != null) endDetour(mod);
        mod.getClientBaritone().getExploreProcess().onLostControl();
        mod.getClientBaritone().getCustomGoalProcess().onLostControl();
        _completedAtStop = isFinished(mod);
        if (_completedAtStop) {
            if (_increaseRange) {
                _wanderDistanceExtension += _distanceToWander;
                Debug.logMessage("Increased wander range");
            }
        }
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        if (_attempt.needsStart() || _origin == null) return false;

        if (_timeCap.elapsed()) return true;

        if (Float.isInfinite(_distanceToWander)) return false;

        // If we fail 10 times or more, we may as well try the previous task again.
        if (_failCounter > 10) {
            return true;
        }

        if (mod.getPlayer() != null && mod.getPlayer().getPos() != null) {
            double sqDist = mod.getPlayer().getPos().squaredDistanceTo(_origin);
            double toWander = _distanceToWander + _wanderDistanceExtension;
            return sqDist > toWander * toWander;
        } else {
            return false;
        }
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof TimeoutWanderTask task) {
            return WanderPolicy.equivalent(_distanceToWander, _increaseRange, _forceExplore,
                    task._distanceToWander, task._increaseRange, task._forceExplore);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Wander for " + (_distanceToWander + _wanderDistanceExtension) + " blocks";
    }

    // This happens all the time in mineshafts and swamps/jungles
    private BlockPos stuckInBlock(Vibrato mod) {
        BlockPos p = mod.getPlayer().getBlockPos();
        if (isAnnoying(mod, p)) return p;
        if (isAnnoying(mod, p.up())) return p.up();
        BlockPos[] toCheck = generateSides(p);
        for (BlockPos check : toCheck) {
            if (isAnnoying(mod, check)) {
                return check;
            }
        }
        BlockPos[] toCheckHigh = generateSides(p.up());
        for (BlockPos check : toCheckHigh) {
            if (isAnnoying(mod, check)) {
                return check;
            }
        }
        return null;
    }

    private boolean isAnnoying(Vibrato mod, BlockPos pos) {
        return ANNOYING_STUCK_BLOCKS.contains(mod.getWorld().getBlockState(pos).getBlock());
        //mod.getWorld().getBlockState(pos).getBlock() instanceof FenceBlock;
    }

    private Task getFenceUnstuckTask(Vibrato mod, BlockPos fencePos) {
        return new SafeRandomShimmyTask();
    }
}
