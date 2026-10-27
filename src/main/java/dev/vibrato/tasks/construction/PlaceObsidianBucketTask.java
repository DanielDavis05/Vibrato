package dev.vibrato.tasks.construction;

import dev.vibrato.Vibrato;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.InteractWithBlockTask;
import dev.vibrato.tasks.movement.GetToBlockTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.WorldHelper;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.progresscheck.MovementProgressChecker;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

import java.util.function.Predicate;

/**
 * Places obsidian at a position using buckets and a cast.
 */
public class PlaceObsidianBucketTask extends Task {

    private final MovementProgressChecker _progressChecker = new MovementProgressChecker();
    private final TimeoutWanderTask _wanderTask = new TimeoutWanderTask(5, true); // This can get stuck forever, so we increase the range.

    public static final Vec3i[] CAST_FRAME = new Vec3i[]{
            new Vec3i(0, -1, 0),
            new Vec3i(0, 0, -1),
            new Vec3i(0, 0, 1),
            new Vec3i(-1, 0, 0),
            new Vec3i(1, 0, 0),
            new Vec3i(1, 1, 0)
    };

    private final BlockPos _pos;

    private BlockPos _currentCastTarget;
    private BlockPos _currentDestroyTarget;
    private Task _frameMaterialTask;
    private int _usableBeforeCollection;
    private boolean _checkCollectedMaterials;

    public PlaceObsidianBucketTask(BlockPos pos) {
        _pos = pos;
    }

    @Override
    protected void onStart(Vibrato mod) {
        mod.getBehaviour().push();
        // Don't break cast
        mod.getBehaviour().avoidBlockBreaking(block -> {
            for (Vec3i castPosRelativeToLava : PlaceObsidianBucketTask.CAST_FRAME) {
                BlockPos castPos = _pos.add(castPosRelativeToLava);
                if (block.equals(castPos)) {
                    return true;
                }
            }
            return false;
        });
        // Don't place blocks inside our cast water/lava
        mod.getBehaviour().avoidBlockPlacing(block -> {
            BlockPos waterTarget = _pos.up();
            return block.equals(_pos) || block.equals(waterTarget);
        });

        _progressChecker.reset();
    }

    @Override
    protected Task onTick(Vibrato mod) {

        // Clear leftover water
        if (mod.getBlockTracker().blockIsValid(_pos, Blocks.OBSIDIAN) && mod.getBlockTracker().blockIsValid(_pos.up(), Blocks.WATER)) {
            return new ClearLiquidTask(_pos.up());
        }

        if (!mod.getItemStorage().hasItem(Items.LAVA_BUCKET)) {
            // The only excuse is that we have lava at our position.
            if (!mod.getBlockTracker().blockIsValid(_pos, Blocks.LAVA)) {
                _progressChecker.reset();
                return TaskCatalogue.getItemTask(Items.LAVA_BUCKET, 1);
            }
        }

        // ResourceTask protects cobblestone until its onStop. Return null for
        // one tick after completion so the child can pop its behaviour frame
        // before we count blocks eligible for placement.
        if (_frameMaterialTask != null) {
            if (!_frameMaterialTask.isFinished(mod)) {
                _progressChecker.reset();
                return _frameMaterialTask;
            }
            _frameMaterialTask = null;
            _checkCollectedMaterials = true;
            _progressChecker.reset();
            return null;
        }

        // A completed placement or removal is real progress even when the
        // player did not move. Account for it before deciding to wander.
        boolean completedBlockChange = false;
        if (_currentCastTarget != null && WorldHelper.isSolid(mod, _currentCastTarget)) {
            _currentCastTarget = null;
            completedBlockChange = true;
        }
        if (_currentDestroyTarget != null && !WorldHelper.isSolid(mod, _currentDestroyTarget)) {
            _currentDestroyTarget = null;
            completedBlockChange = true;
        }
        if (completedBlockChange) _progressChecker.reset();

        // A descendant can veto interruption even after the resource target is
        // met. Keep yielding until the entire collection child has stopped and
        // released its protection before counting the newly acquired blocks.
        if (_checkCollectedMaterials && hasChild()) {
            _progressChecker.reset();
            return null;
        }

        int missingFrameBlocks = missingFrameCount(_pos, pos -> WorldHelper.isSolid(mod, pos));
        int usable = PlaceBlockTask.countUsableThrowaways(mod,
                item -> PlaceBlockTask.isStableCastMaterial(mod, item, _pos));
        if (_checkCollectedMaterials) {
            _checkCollectedMaterials = false;
            if (usable < missingFrameBlocks && usable <= _usableBeforeCollection) {
                fail("Collecting cobblestone did not provide usable cast blocks at " + _pos.toShortString());
                return null;
            }
        }
        if (usable < missingFrameBlocks) {
            if (mod.getBehaviour().isProtected(Items.COBBLESTONE)
                    || !mod.getClientBaritoneSettings().acceptableThrowawayItems.value.contains(Items.COBBLESTONE)) {
                fail("Cannot build obsidian cast at " + _pos.toShortString()
                        + ": cobblestone is protected or not an accepted throwaway");
                return null;
            }
            int held = mod.getItemStorage().getItemCountInventoryOnly(Items.COBBLESTONE);
            ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
            if (cursor.isOf(Items.COBBLESTONE)) held -= cursor.getCount();
            _usableBeforeCollection = usable;
            _frameMaterialTask = TaskCatalogue.getItemTask(Items.COBBLESTONE, CastMaterialPolicy.cobblestoneTarget(held, missingFrameBlocks, usable));
            setDebugState("Collecting blocks for cast frame");
            _progressChecker.reset();
            return _frameMaterialTask;
        }

        // Manual wander to try and make this godforesaken task work
        if (!completedBlockChange && _wanderTask.isActive() && !_wanderTask.isFinished(mod)) {
            setDebugState("Wandering.");
            _progressChecker.reset();
            return _wanderTask;
        }
        if (!_progressChecker.check(mod)) {
            _progressChecker.reset();
            return _wanderTask;
        }

        if (_currentCastTarget != null) {
            return new PlaceStructureBlockTask(_currentCastTarget, _pos);
        }
        if (_currentDestroyTarget != null) {
            return new DestroyBlockTask(_currentDestroyTarget);
        }

        // Build the cast frame
        for (Vec3i castPosRelative : CAST_FRAME) {
            BlockPos castPos = _pos.add(castPosRelative);
            if (!WorldHelper.isSolid(mod, castPos)) {
                _currentCastTarget = castPos;
                return null;
            }
        }

        // Cast frame built. Now, place lava.
        if (mod.getWorld().getBlockState(_pos).getBlock() != Blocks.LAVA) {

            if (WorldHelper.isSolid(mod, _pos)) {
                setDebugState("Clearing space around lava");
                _currentDestroyTarget = _pos;
                return null;
                //return new DestroyBlockTask(framePos);
            }
            // Clear the upper two as well, to make placing more reliable.
            if (WorldHelper.isSolid(mod, _pos.up())) {
                setDebugState("Clearing space around lava");
                _currentDestroyTarget = _pos.up();
                return null;
            }
            if (WorldHelper.isSolid(mod, _pos.up(2))) {
                setDebugState("Clearing space around lava");
                _currentDestroyTarget = _pos.up(2);
                return null;
            }

            // Make sure we have water, juuust in case we have another creeper appear run end here
            if (!mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {
                _progressChecker.reset();
                return TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1);
            }

            // Don't place lava at our position!
            // Would lead to an embarrassing death.
            BlockPos targetPos = _pos.add(-1, 1, 0);
            if (!mod.getPlayer().getBlockPos().equals(targetPos) && mod.getItemStorage().hasItem(Items.LAVA_BUCKET)) {
                setDebugState("Positioning player before lava");
                return new GetToBlockTask(targetPos, false);
            }

            setDebugState("Placing lava for cast");

            return new InteractWithBlockTask(new ItemTarget(Items.LAVA_BUCKET, 1), Direction.WEST, _pos.add(1, 0, 0), false);
        }
        // Lava placed, Now, place water.
        BlockPos waterCheck = _pos.up();
        if (mod.getWorld().getBlockState(waterCheck).getBlock() != Blocks.WATER) {
            setDebugState("Placing water for cast");

            if (WorldHelper.isSolid(mod, waterCheck)) {
                _currentDestroyTarget = waterCheck;
                return null;
                //return new DestroyBlockTask(waterCheck);

            }
            if (WorldHelper.isSolid(mod, waterCheck.up())) {
                _currentDestroyTarget = waterCheck.up();
                return null;
                //return new DestroyBlockTask(waterCheck.up());
            }

            // Get to position to avoid weird stuck scenario
            BlockPos targetPos = _pos.add(-1, 1, 0);
            if (!mod.getPlayer().getBlockPos().equals(targetPos) && mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {
                setDebugState("Positioning player before water");
                return new GetToBlockTask(targetPos, false);
            }

            return new InteractWithBlockTask(new ItemTarget(Items.WATER_BUCKET, 1), Direction.WEST, _pos.add(1, 1, 0), true);
        }
        return null;
    }

    static int missingFrameCount(BlockPos origin, Predicate<BlockPos> solid) {
        int missing = 0;
        for (Vec3i offset : CAST_FRAME) if (!solid.test(origin.add(offset))) missing++;
        return missing;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        mod.getBehaviour().pop();
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return mod.getBlockTracker().blockIsValid(_pos, Blocks.OBSIDIAN) && !mod.getBlockTracker().blockIsValid(_pos.up(), Blocks.WATER);
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof PlaceObsidianBucketTask task) {
            return task._pos.equals(_pos);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Placing obsidian at " + _pos + " with a cast";
    }

    public BlockPos getPos() {
        return _pos;
    }
}
