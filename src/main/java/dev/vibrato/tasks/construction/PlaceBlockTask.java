package dev.vibrato.tasks.construction;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.movement.GetToBlockTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.ITaskRequiresGrounded;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.WorldHelper;
import dev.vibrato.util.progresscheck.MovementProgressChecker;
import baritone.api.schematic.AbstractSchematic;
import baritone.api.schematic.ISchematic;
import baritone.api.utils.BlockOptionalMeta;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Place a block type at a position
 */
public class PlaceBlockTask extends Task implements ITaskRequiresGrounded {

    private static final int MIN_MATERIALS = 1;
    private static final int PREFERRED_MATERIALS = 32;
    private final BlockPos _target;
    private final Block[] _toPlace;
    private final boolean _useThrowaways;
    private final boolean _autoCollectStructureBlocks;
    private final MovementProgressChecker _progressChecker = new MovementProgressChecker();
    private final TimeoutWanderTask _wanderTask = new TimeoutWanderTask(3, true); // This can get stuck forever, so we increase the range.
    private Task _materialTask;
    private int _failCount = 0;

    public PlaceBlockTask(BlockPos target, Block[] toPlace, boolean useThrowaways, boolean autoCollectStructureBlocks) {
        _target = target;
        _toPlace = toPlace;
        _useThrowaways = useThrowaways;
        _autoCollectStructureBlocks = autoCollectStructureBlocks;
    }

    public PlaceBlockTask(BlockPos target, Block... toPlace) {
        this(target, toPlace, false, false);
    }

    public static int getMaterialCount(Vibrato mod) {
        return mod.getItemStorage().getItemCount(Items.DIRT, Items.COBBLESTONE, Items.NETHERRACK);
    }

    public static Task getMaterialTask(int count) {
        return TaskCatalogue.getSquashedItemTask(new ItemTarget(Items.DIRT, count), new ItemTarget(Items.COBBLESTONE, count), new ItemTarget(Items.NETHERRACK, count));
    }

    @Override
    protected void onStart(Vibrato mod) {
        _progressChecker.reset();
        // If we get interrupted by another task, this might cause problems...
        //_wanderTask.resetWander();
    }

    @Override
    protected Task onTick(Vibrato mod) {

        // Perform timeout wander
        if (_wanderTask.isActive() && !_wanderTask.isFinished(mod)) {
            setDebugState("Wandering.");
            _progressChecker.reset();
            return _wanderTask;
        }

        if (_autoCollectStructureBlocks) {
            if (_materialTask != null && _materialTask.isActive() && !_materialTask.isFinished(mod)) {
                setDebugState("No structure items, collecting cobblestone + dirt as default.");
                if (getMaterialCount(mod) < PREFERRED_MATERIALS) {
                    return _materialTask;
                } else {
                    _materialTask = null;
                }
            }

            //Item[] items = Util.toArray(Item.class, mod.getClientBaritoneSettings().acceptableThrowawayItems.value);
            if (getMaterialCount(mod) < MIN_MATERIALS) {
                // TODO: Mine items, extract their resource key somehow.
                _materialTask = getMaterialTask(PREFERRED_MATERIALS);
                _progressChecker.reset();
                return _materialTask;
            }
        }


        // Check if we're approaching our point. If we fail, wander for a bit.
        if (!_progressChecker.check(mod)) {
            _failCount++;
            if (!tryingAlternativeWay()) {
                Debug.logMessage("Failed to place, wandering timeout.");
                return _wanderTask;
            } else {
                Debug.logMessage("Trying alternative way of placing block...");
            }
        }


        // Place block
        if (tryingAlternativeWay()) {
            setDebugState("Alternative way: Trying to go above block to place block.");
            return new GetToBlockTask(_target.up(), false);
        } else {
            setDebugState("Letting baritone place a block.");

            // Perform baritone placement
            if (!mod.getClientBaritone().getBuilderProcess().isActive()) {
                Debug.logInternal("Run Structure Build");
                ISchematic schematic = new PlaceStructureSchematic(mod);
                mod.getClientBaritone().getBuilderProcess().build("structure", schematic, _target);
            }
        }

        return null;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        mod.getClientBaritone().getBuilderProcess().onLostControl();
    }

    //TODO: Place structure where a leaf block was???? Might need to delete the block first if it's not empty/air/water.

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof PlaceBlockTask task) {
            return task._target.equals(_target) && task._useThrowaways == _useThrowaways && Arrays.equals(task._toPlace, _toPlace);
        }
        return false;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        assert MinecraftClient.getInstance().world != null;
        if (_useThrowaways) {
            return WorldHelper.isSolid(mod, _target);
        }
        BlockState state = mod.getWorld().getBlockState(_target);
        return ArrayUtils.contains(_toPlace, state.getBlock());
    }

    @Override
    protected String toDebugString() {
        return "Place structure" + ArrayUtils.toString(_toPlace) + " at " + _target.toShortString();
    }

    private boolean tryingAlternativeWay() {
        return _failCount % 4 == 3;
    }

    private class PlaceStructureSchematic extends AbstractSchematic {

        private final Vibrato _mod;
        // Baritone queries this many times per tick, only complain once.
        private boolean _warnedNoThrowaway = false;

        public PlaceStructureSchematic(Vibrato mod) {
            super(1, 1, 1);
            _mod = mod;
        }

        @Override
        public BlockState desiredState(int x, int y, int z, BlockState blockState, List<BlockState> available) {
            if (x == 0 && y == 0 && z == 0) {
                // Place!!
                for (BlockState possible : available) {
                    if (possible == null) continue;
                    if (_useThrowaways && _mod.getClientBaritoneSettings().acceptableThrowawayItems.value.contains(possible.getBlock().asItem())
                            && !_mod.getBehaviour().isProtected(possible.getBlock().asItem())) {
                        return possible;
                    }
                    if (!_useThrowaways && Arrays.asList(_toPlace).contains(possible.getBlock())) {
                        return possible;
                    }
                }
                // Builder candidates can be transiently empty before its first
                // calculation. Keep a solid goal from actual main inventory so it
                // can plan and move the item to the hotbar. Inventory slot order is
                // deterministic, and protected resources are never throwaways.
                if (_mod.getPlayer() != null) {
                    for (ItemStack stack : _mod.getPlayer().getInventory().main) {
                        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) continue;
                        Block block = item.getBlock();
                        if (_useThrowaways
                                ? _mod.getClientBaritoneSettings().acceptableThrowawayItems.value.contains(stack.getItem())
                                    && !_mod.getBehaviour().isProtected(stack.getItem())
                                : Arrays.asList(_toPlace).contains(block)) {
                            return block.getDefaultState();
                        }
                    }
                }
                if (!_warnedNoThrowaway) {
                    _warnedNoThrowaway = true;
                    Debug.logInternal("No requested placeable block in player inventory at "
                            + _target.toShortString() + "; throwaway=" + _useThrowaways
                            + ", requested=" + Arrays.toString(_toPlace));
                }
                // For explicit placement, keep the requested goal so the builder can
                // report missing materials instead of silently substituting cobblestone.
                if (!_useThrowaways && _toPlace.length > 0) {
                    return new BlockOptionalMeta(_toPlace[0]).getAnyBlockState();
                }
                return Blocks.AIR.getDefaultState();
            }
            // Don't care.
            return blockState;
        }
    }
}
