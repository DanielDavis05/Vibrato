package dev.vibrato.tasks.resources;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.DoToClosestBlockTask;
import dev.vibrato.tasks.InteractWithBlockTask;
import dev.vibrato.tasks.ResourceTask;
import dev.vibrato.tasks.construction.DestroyBlockTask;
import dev.vibrato.tasks.slot.EnsureFreeCursorSlotTask;
import dev.vibrato.tasks.movement.*;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.Dimension;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.time.TimerGame;
import dev.vibrato.util.helpers.LookHelper;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.helpers.WorldHelper;
import dev.vibrato.util.progresscheck.MovementProgressChecker;
import baritone.api.utils.input.Input;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.RaycastContext;

import java.util.HashSet;
import java.util.Optional;
import java.util.function.Predicate;

public class CollectBucketLiquidTask extends ResourceTask {

    private final HashSet<BlockPos> _blacklist = new HashSet<>();
    private final TimerGame _tryImmediatePickupTimer = new TimerGame(3);
    private final TimerGame _pickedUpTimer = new TimerGame(0.5);
    private final TimerGame _localSourceTimer = new TimerGame(0.5);
    private Optional<BlockPos> _localSource = Optional.empty();
    private boolean _localScanInitialized;
    private final int _count;

    //private IProgressChecker<Double> _checker = new LinearProgressChecker(5, 0.1);
    private final Item _target;
    private final Block _toCollect;
    private final String _liquidName;
    private final TimeoutWanderTask _wanderTask = new TimeoutWanderTask(15f);
    private final MovementProgressChecker _progressChecker = new MovementProgressChecker();

    private boolean wasWandering = false;

    public CollectBucketLiquidTask(String liquidName, Item filledBucket, int targetCount, Block toCollect) {
        super(filledBucket, targetCount);
        _liquidName = liquidName;
        _target = filledBucket;
        _count = targetCount;
        _toCollect = toCollect;
    }

    @Override
    protected boolean shouldAvoidPickingUp(Vibrato mod) {
        return false;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void onResourceStart(Vibrato mod) {
        // Track fluids
        mod.getBehaviour().push();
        mod.getBehaviour().setRayTracingFluidHandling(RaycastContext.FluidHandling.SOURCE_ONLY);
        mod.getBlockTracker().trackBlock(_toCollect);

        // Avoid breaking / placing blocks at our liquid
        mod.getBehaviour().avoidBlockBreaking((pos) -> MinecraftClient.getInstance().world.getBlockState(pos).getBlock() == _toCollect);
        mod.getBehaviour().avoidBlockPlacing((pos) -> MinecraftClient.getInstance().world.getBlockState(pos).getBlock() == _toCollect);

        //_blacklist.clear();

        _wanderTask.resetWander();

        _progressChecker.reset();
        _localSource = Optional.empty();
        _localScanInitialized = false;
    }


    @Override
    protected Task onTick(Vibrato mod) {
        // A forced descendant may keep this task ticking after its goal is met.
        // Consult the actual main inventory before the submerged pickup shortcut
        // (and before scheduling any other interaction) to avoid filling another
        // empty bucket while the completed child is being released.
        if (BucketCollectionPolicy.goalMet(_count, mainInventoryCount(mod, _target))) {
            mod.getExtraBaritoneSettings().setInteractionPaused(false);
            return null;
        }
        Task result = super.onTick(mod);
        // Reset our "first time" timeout/wander flag.
        if (!thisOrChildAreTimedOut()) {
            wasWandering = false;
        }
        return result;
    }

    @Override
    protected Task onResourceTick(Vibrato mod) {

        // A filled result on the cursor is not a free slot for a new pickup.
        // Let it settle in the inventory, including for multi-bucket targets.
        if (StorageHelper.getItemStackInCursorSlot().isOf(_target)) {
            _progressChecker.reset();
            setDebugState("Moving filled bucket from cursor to inventory");
            return new EnsureFreeCursorSlotTask();
        }

        // Run one update to prevent the false fail bug?
        _progressChecker.check(mod);

        // If we're standing inside a liquid, go pick it up.
        if (_tryImmediatePickupTimer.elapsed()) {
            Block standingInside = mod.getWorld().getBlockState(mod.getPlayer().getBlockPos()).getBlock();
            if (standingInside == _toCollect) {
                setDebugState("Trying to collect (we are in it)");
                mod.getInputControls().forceLook(0, 90);
                //mod.getClientBaritone().getLookBehavior().updateTarget(new Rotation(0, 90), true);
                //Debug.logMessage("Looking at " + _toCollect + ", picking up right away.");
                _tryImmediatePickupTimer.reset();
                if (mod.getSlotHandler().forceEquipItem(Items.BUCKET)) {
                    mod.getInputControls().tryPress(Input.CLICK_RIGHT);
                    mod.getExtraBaritoneSettings().setInteractionPaused(true);
                    _pickedUpTimer.reset();
                    _progressChecker.reset();
                }
                return null;
            }
        }

        if (!_pickedUpTimer.elapsed()) {
            mod.getExtraBaritoneSettings().setInteractionPaused(false);
            _progressChecker.reset();
            // Wait for force pickup
            return null;
        }

        if (_wanderTask.isActive() && !_wanderTask.isFinished(mod)) {
            setDebugState("Failed to receive: Wandering.");
            _progressChecker.reset();
            return _wanderTask;
        }

        // Get buckets if we need em
        int bucketGoal = BucketCollectionPolicy.emptyBucketGoal(_count, mainInventoryCount(mod, _target));
        if (mod.getItemStorage().getItemCountInventoryOnly(Items.BUCKET) < bucketGoal) {
            setDebugState("Getting bucket...");
            // ResourceTask expects the desired total, not the shortfall.
            return TaskCatalogue.getItemTask(Items.BUCKET, bucketGoal);
        }

        Predicate<BlockPos> isSourceLiquid = blockPos -> {
            if (_blacklist.contains(blockPos)) return false;
            if (!WorldHelper.canReach(mod, blockPos)) return false;
            if (!WorldHelper.canReach(mod, blockPos.up())) return false; // We may try reaching the block above.
            assert MinecraftClient.getInstance().world != null;

            // Lava, we break the block above. If it's bedrock, ignore.
            if (_toCollect == Blocks.LAVA && !WorldHelper.canBreak(mod, blockPos.up())) {
                return false;
            }

            return WorldHelper.isSourceBlock(mod, blockPos, false);
        };

        // Find nearest water and right click it
        if (localSource(mod, isSourceLiquid).isPresent()
                || mod.getBlockTracker().anyFound(isSourceLiquid, _toCollect)) {
            // We want to MINIMIZE this distance to liquid.
            setDebugState("Trying to collect...");
            //Debug.logMessage("TEST: " + RayTraceUtils.fluidHandling);

            return new DoToClosestBlockTask(blockPos -> {
                // Clear above if lava because we can't enter.
                // but NOT if we're standing right above.
                if (WorldHelper.isSolid(mod, blockPos.up())) {

                    if (!_progressChecker.check(mod)) {
                        Debug.logInternal("Failed to break, blacklisting & wandering");
                        mod.getBlockTracker().requestBlockUnreachable(blockPos);
                        _blacklist.add(blockPos);
                        return _wanderTask;
                    }
                    return new DestroyBlockTask(blockPos.up());
                }

                // We can reach the block.
                if (LookHelper.getReach(blockPos).isPresent()) {
                    return new InteractWithBlockTask(new ItemTarget(Items.BUCKET, 1), blockPos, _toCollect != Blocks.LAVA, new Vec3i(0, 1, 0));
                }
                // Get close enough.
                // up because if we go below we'll try to move next to the liquid (for lava, not a good move)
                if (this.thisOrChildAreTimedOut() && !wasWandering) {
                    mod.getBlockTracker().requestBlockUnreachable(blockPos.up());
                    wasWandering = true;
                }
                return new GetCloseToBlockTask(blockPos.up());
            }, origin -> localSource(mod, isSourceLiquid)
                    .or(() -> mod.getBlockTracker().getNearestTracking(origin, isSourceLiquid, _toCollect)),
                    isSourceLiquid, _toCollect);
        }

        // Dimension
        if (_toCollect == Blocks.WATER && WorldHelper.getCurrentDimension() == Dimension.NETHER) {
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        // Oof, no liquid found.
        setDebugState("Searching for liquid by wandering around aimlessly");

        return new TimeoutWanderTask(Float.POSITIVE_INFINITY);
    }

    private static int mainInventoryCount(Vibrato mod, Item item) {
        return BucketCollectionPolicy.countMain(mod.getPlayer().getInventory().main,
                stack -> !stack.isEmpty() && stack.isOf(item), ItemStack::getCount);
    }

    private Optional<BlockPos> localSource(Vibrato mod, Predicate<BlockPos> isSourceLiquid) {
        boolean invalid = _localSource.isPresent() && (!_localSource.get().isWithinDistance(mod.getPlayer().getPos(), 16)
                || !mod.getChunkTracker().isChunkLoaded(_localSource.get())
                || !mod.getBlockTracker().blockIsValid(_localSource.get(), _toCollect)
                || !isSourceLiquid.test(_localSource.get()));
        if (!_localScanInitialized || invalid || _localSourceTimer.elapsed()) {
            _localSource = mod.getBlockTracker().getNearestWithinRange(mod.getPlayer().getPos(), 16,
                    isSourceLiquid, _toCollect);
            _localSourceTimer.reset();
            _localScanInitialized = true;
        }
        return _localSource;
    }

    @Override
    protected void onResourceStop(Vibrato mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(_toCollect);
        mod.getBehaviour().pop();
        //mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
        mod.getExtraBaritoneSettings().setInteractionPaused(false);
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        if (other instanceof CollectBucketLiquidTask task) {
            if (task._count != _count) return false;
            return task._toCollect == _toCollect;
        }
        return false;
    }

    @Override
    protected String toDebugStringName() {
        return "Collect " + _count + " " + _liquidName + " buckets";
    }

    public static class CollectWaterBucketTask extends CollectBucketLiquidTask {
        public CollectWaterBucketTask(int targetCount) {
            super("water", Items.WATER_BUCKET, targetCount, Blocks.WATER);
        }
    }

    public static class CollectLavaBucketTask extends CollectBucketLiquidTask {
        public CollectLavaBucketTask(int targetCount) {
            super("lava", Items.LAVA_BUCKET, targetCount, Blocks.LAVA);
        }
    }

}
