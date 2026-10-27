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
import dev.vibrato.util.helpers.LookHelper;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.time.TimerGame;
import dev.vibrato.util.progresscheck.MovementProgressChecker;
import baritone.api.schematic.AbstractSchematic;
import baritone.api.schematic.ISchematic;
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.MovementHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
    private static final Direction[] SUPPORT_ORDER = {Direction.DOWN, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST, Direction.UP};
    private final TimerGame _manualClickTimer = new TimerGame(0.25);
    private boolean _manualControl;
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
        _manualControl = false;
        // If we get interrupted by another task, this might cause problems...
        //_wanderTask.resetWander();
    }

    @Override
    protected Task onTick(Vibrato mod) {

        // Perform timeout wander
        if (_wanderTask.isActive() && !_wanderTask.isFinished(mod)) {
            releaseManualControl(mod);
            setDebugState("Wandering.");
            _progressChecker.reset();
            return _wanderTask;
        }

        if (_autoCollectStructureBlocks) {
            if (_materialTask != null && _materialTask.isActive() && !_materialTask.isFinished(mod)) {
                setDebugState("No structure items, collecting cobblestone + dirt as default.");
                if (getMaterialCount(mod) < PREFERRED_MATERIALS) {
                    releaseManualControl(mod);
                    return _materialTask;
                } else {
                    _materialTask = null;
                }
            }

            //Item[] items = Util.toArray(Item.class, mod.getClientBaritoneSettings().acceptableThrowawayItems.value);
            if (getMaterialCount(mod) < MIN_MATERIALS) {
                // TODO: Mine items, extract their resource key somehow.
                _materialTask = getMaterialTask(PREFERRED_MATERIALS);
                releaseManualControl(mod);
                _progressChecker.reset();
                return _materialTask;
            }
        }


        // Check if we're approaching our point. If we fail, wander for a bit.
        if (!_progressChecker.check(mod)) {
            _failCount++;
            if (!tryingAlternativeWay()) {
                releaseManualControl(mod);
                Debug.logMessage("Failed to place, wandering timeout.");
                return _wanderTask;
            } else {
                Debug.logMessage("Trying alternative way of placing block...");
            }
        }


        // Reachable placements can be aimed directly; the native builder handles
        // cases that need movement, airborne placement, or obstruction handling.
        if (!tryingAlternativeWay() && tryManualPlace(mod)) return null;
        releaseManualControl(mod);

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
        releaseManualControl(mod);
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

    private ItemStack findPlaceableInventoryStack(Vibrato mod) {
        for (ItemStack stack : mod.getPlayer().getInventory().main) {
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) continue;
            if (_useThrowaways
                    ? mod.getClientBaritoneSettings().acceptableThrowawayItems.value.contains(stack.getItem())
                        && !mod.getBehaviour().isProtected(stack.getItem())
                    : Arrays.asList(_toPlace).contains(item.getBlock())) return stack;
        }
        return ItemStack.EMPTY;
    }

    static boolean hitPlacesAt(BlockHitResult hit, BlockPos support, Direction face, BlockPos target) {
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(support)
                && hit.getSide() == face && support.offset(face).equals(target);
    }

    private boolean tryManualPlace(Vibrato mod) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!mod.getPlayer().isOnGround() || client.currentScreen != null
                || !StorageHelper.getItemStackInCursorSlot().isEmpty()
                || !mod.getWorld().getBlockState(_target).isReplaceable()
                || !WorldHelper.canPlace(mod, _target)
                || mod.getPlayer().getBoundingBox().intersects(new Box(_target))) return false;
        ItemStack stack = findPlaceableInventoryStack(mod);
        if (stack.isEmpty()) return false;
        Item material = stack.getItem();

        BlockPos support = null;
        Direction face = null;
        Rotation rotation = null;
        for (Direction direction : SUPPORT_ORDER) {
            BlockPos neighbor = _target.offset(direction);
            Direction side = direction.getOpposite();
            if (!MovementHelper.canPlaceAgainst(mod.getClientBaritone().getPlayerContext(), neighbor)) continue;
            Optional<Rotation> reachable = LookHelper.getReach(neighbor, side);
            if (reachable.isPresent()) {
                support = neighbor;
                face = side;
                rotation = reachable.get();
                break;
            }
        }
        if (support == null) return false;
        if (!_manualControl) {
            mod.getClientBaritone().getBuilderProcess().onLostControl();
            mod.getClientBaritone().getPathingBehavior().cancelEverything();
            _manualControl = true;
        }
        setDebugState("Aiming to place on " + support.toShortString());
        mod.getInputControls().hold(Input.SNEAK);
        mod.getSlotHandler().forceEquipItem(material);
        LookHelper.lookAt(mod, rotation);
        if (mod.getExtraBaritoneSettings().isInteractionPaused() || !mod.getPlayer().isSneaking()
                || !StorageHelper.isEquipped(mod, material)
                || !_manualClickTimer.elapsed() || client.interactionManager == null
                || !(client.crosshairTarget instanceof BlockHitResult hit)
                || !hitPlacesAt(hit, support, face, _target)) return true;
        _manualClickTimer.reset();
        ActionResult result = client.interactionManager.interactBlock(mod.getPlayer(), Hand.MAIN_HAND, hit);
        if (result.isAccepted()) mod.getPlayer().swingHand(Hand.MAIN_HAND);
        return true;
    }

    private void releaseManualControl(Vibrato mod) {
        if (_manualControl) {
            mod.getInputControls().release(Input.SNEAK);
            _manualControl = false;
        }
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
                    ItemStack stack = findPlaceableInventoryStack(_mod);
                    if (!stack.isEmpty()) return ((BlockItem) stack.getItem()).getBlock().getDefaultState();
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
