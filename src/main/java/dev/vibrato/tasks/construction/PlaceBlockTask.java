package dev.vibrato.tasks.construction;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.eventbus.EventBus;
import dev.vibrato.eventbus.Subscription;
import dev.vibrato.eventbus.events.BlockInteractEvent;
import dev.vibrato.tasks.movement.GetToBlockTask;
import dev.vibrato.tasks.movement.GetWithinRangeOfBlockTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasks.slot.ClickSlotTask;
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
import net.minecraft.block.FallingBlock;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Predicate;

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
    private final BlockPos _castOrigin;
    private final MovementProgressChecker _progressChecker = new MovementProgressChecker();
    private final TimeoutWanderTask _wanderTask = new TimeoutWanderTask(3, true); // This can get stuck forever, so we increase the range.
    private static final Direction[] SUPPORT_ORDER = {Direction.DOWN, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST, Direction.UP};
    private final TimerGame _manualClickTimer = new TimerGame(0.25);
    private boolean _manualControl;
    private Task _materialTask;
    private boolean _checkCollectedMaterials;
    private int _failCount = 0;
    private int _handoffTicksRemaining;
    private PlacementAttempt _placementAttempt;
    private Subscription<BlockInteractEvent> _placementInteraction;
    // Recovery is opt-in for blueprint placements; other callers retain their existing behavior.
    private Set<BlockPos> _footprint;
    private Map<BlockPos, Block> _supportBlocks;
    private List<BlockPos> _selectedRoute;
    private static final int MAX_SUPPORT_BLOCKS = 512;
    private BlockPos _recoveryStand;
    private BlockPos _scaffold;
    private int _scaffoldDepth;
    private boolean _scaffoldMaterialsReady;
    private Item _scaffoldMaterial;
    private Task _recoveryChild;
    private int _recoveryAttempts;
    private final Set<BlockPos> _failedRecoveryStands = new HashSet<>();
    private final TimerGame _recoveryTimer = new TimerGame(45);
    private final TimerGame _directManualTimer = new TimerGame(20);
    private boolean _directManualStarted;
    private final TimerGame _groundWaitTimer = new TimerGame(3);
    private boolean _recoveryWaitingForGround;
    private boolean _recoveryRestartPending;
    private final TimerGame _siteApproachTimer = new TimerGame(90);
    private boolean _siteApproachStarted;
    private Task _siteApproachTask;

    public PlaceBlockTask(BlockPos target, Block block, Set<BlockPos> footprint) {
        this(target, block, footprint, new HashMap<>());
    }

    public PlaceBlockTask(BlockPos target, Block block, Set<BlockPos> footprint, Map<BlockPos, Block> supports) {
        this(target, block);
        _footprint = Set.copyOf(footprint);
        _supportBlocks = supports;
    }

    public PlaceBlockTask(BlockPos target, Block[] toPlace, boolean useThrowaways, boolean autoCollectStructureBlocks) {
        this(target, toPlace, useThrowaways, autoCollectStructureBlocks, null);
    }

    protected PlaceBlockTask(BlockPos target, Block[] toPlace, boolean useThrowaways,
                             boolean autoCollectStructureBlocks, BlockPos castOrigin) {
        _target = target;
        _toPlace = toPlace;
        _useThrowaways = useThrowaways;
        _autoCollectStructureBlocks = autoCollectStructureBlocks;
        _castOrigin = castOrigin;
    }

    public PlaceBlockTask(BlockPos target, Block... toPlace) {
        this(target, toPlace, false, false);
    }

    PlaceBlockTask(BlockPos target, Block[] toPlace, boolean pendingScreenHandoff) {
        this(target, toPlace, false, false);
        _handoffTicksRemaining = pendingScreenHandoff ? 20 : 0;
    }

    public static int getMaterialCount(Vibrato mod) {
        return mod.getItemStorage().getItemCount(Items.DIRT, Items.COBBLESTONE, Items.NETHERRACK);
    }

    public static Task getMaterialTask(int count) {
        return TaskCatalogue.getSquashedItemTask(new ItemTarget(Items.DIRT, count), new ItemTarget(Items.COBBLESTONE, count), new ItemTarget(Items.NETHERRACK, count));
    }

    static int countUsableThrowaways(Iterable<ItemStack> mainInventory, Predicate<Item> acceptable, Predicate<Item> protectedItem) {
        return CastMaterialPolicy.countUsable(mainInventory,
                stack -> !stack.isEmpty() && stack.getItem() instanceof BlockItem
                        && acceptable.test(stack.getItem()) && !protectedItem.test(stack.getItem()),
                ItemStack::getCount);
    }

    static boolean isStableSolidMaterial(Vibrato mod, Item item, BlockPos pos) {
        if (!(item instanceof BlockItem blockItem) || blockItem.getBlock() instanceof FallingBlock) return false;
        return blockItem.getBlock().getDefaultState().isSolidBlock(mod.getWorld(), pos);
    }

    static boolean isStableCastMaterial(Vibrato mod, Item item, BlockPos origin) {
        for (var offset : PlaceObsidianBucketTask.CAST_FRAME) {
            BlockPos pos = origin.add(offset);
            if (!WorldHelper.isSolid(mod, pos) && !isStableSolidMaterial(mod, item, pos)) return false;
        }
        return true;
    }

    static int countUsableThrowaways(Vibrato mod) {
        return countUsableThrowaways(mod, item -> true);
    }

    static int countUsableThrowaways(Vibrato mod, Predicate<Item> additionalFilter) {
        return countUsableThrowaways(mod.getPlayer().getInventory().main,
                item -> mod.getClientBaritoneSettings().acceptableThrowawayItems.value.contains(item)
                        && additionalFilter.test(item),
                item -> mod.getBehaviour().isProtected(item));
    }

    @Override
    protected void onStart(Vibrato mod) {
        _progressChecker.reset();
        if (_footprint != null) {
            mod.getBehaviour().push();
            mod.getBehaviour().avoidBlockBreaking(pos -> _supportBlocks != null && mod.getWorld() != null
                    && _supportBlocks.get(pos) == mod.getWorld().getBlockState(pos).getBlock());
        }
        _manualControl = false;
        _recoveryWaitingForGround = false;
        _placementAttempt = new PlacementAttempt();
        _placementInteraction = EventBus.subscribe(BlockInteractEvent.class, event -> {
            BlockHitResult hit = event.hitResult;
            if (event.world == mod.getWorld()
                    && hit.getBlockPos().offset(hit.getSide()).equals(_target)
                    && isPlaceableStack(mod, mod.getPlayer().getStackInHand(event.hand))) {
                _placementAttempt.interacted();
            }
        });
        // If we get interrupted by another task, this might cause problems...
        //_wanderTask.resetWander();
    }

    @Override
    protected Task onTick(Vibrato mod) {

        // A successful last-item placement can empty inventory before the
        // target block update arrives. Resolve that particular interaction
        // before reporting missing material or acquiring more.
        if (isFinished(mod)) return null;
        if (_placementAttempt.waitForResult(!findPlaceableInventoryStack(mod).isEmpty())) {
            releaseManualControl(mod);
            _progressChecker.reset();
            setDebugState("Waiting for placement result");
            return null;
        }

        // Perform timeout wander
        if (_wanderTask.isActive() && !_wanderTask.isFinished(mod)) {
            releaseManualControl(mod);
            setDebugState("Wandering.");
            _progressChecker.reset();
            return _wanderTask;
        }

        if (_autoCollectStructureBlocks) {
            if (_materialTask != null) {
                if (!_materialTask.isFinished(mod)) {
                    releaseManualControl(mod);
                    _progressChecker.reset();
                    return _materialTask;
                }
                // Stop the resource child before checking usable materials: it
                // protects its result until onStop pops its behaviour frame.
                _materialTask = null;
                _checkCollectedMaterials = true;
                return null;
            }
            // Resource descendants may veto interruption while finishing a
            // pickup. Their protected behaviour frame remains until detachment.
            if (_checkCollectedMaterials && hasChild()) {
                _progressChecker.reset();
                return null;
            }
            int usable = countUsableThrowaways(mod);
            if (_checkCollectedMaterials) {
                _checkCollectedMaterials = false;
                if (usable < MIN_MATERIALS) {
                    fail("Collecting cobblestone did not provide a usable structure block");
                    return null;
                }
            }
            if (usable < MIN_MATERIALS) {
                if (mod.getBehaviour().isProtected(Items.COBBLESTONE)
                        || !mod.getClientBaritoneSettings().acceptableThrowawayItems.value.contains(Items.COBBLESTONE)) {
                    fail("Cannot collect a usable structure block: cobblestone is protected or not an accepted throwaway");
                    return null;
                }
                int held = mod.getItemStorage().getItemCountInventoryOnly(Items.COBBLESTONE);
                ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
                if (cursor.isOf(Items.COBBLESTONE)) held -= cursor.getCount();
                _materialTask = TaskCatalogue.getItemTask(Items.COBBLESTONE, held + PREFERRED_MATERIALS);
                releaseManualControl(mod);
                _progressChecker.reset();
                return _materialTask;
            }
        }

        ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
        PlacementMaterialPolicy.Action availability = PlacementMaterialPolicy.decide(
                !findPlaceableInventoryStack(mod).isEmpty(), isPlaceableStack(mod, cursor), _handoffTicksRemaining);
        switch (availability) {
            case MOVE_CURSOR -> {
                releaseManualControl(mod);
                var slot = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursor, true);
                if (slot.isEmpty()) {
                    fail("No inventory space for requested placement material at " + _target.toShortString());
                    return null;
                }
                _progressChecker.reset();
                setDebugState("Moving placement material from cursor to inventory");
                return new ClickSlotTask(slot.get());
            }
            case WAIT -> {
                _handoffTicksRemaining--;
                _progressChecker.reset();
                setDebugState("Waiting for container inventory handoff");
                return null;
            }
            case FAIL -> {
                releaseManualControl(mod);
                fail("No usable block to place at " + _target.toShortString()
                        + (_useThrowaways ? " (no unprotected throwaway in main inventory)" : " (requested " + Arrays.toString(_toPlace) + ")"));
                return null;
            }
            case READY -> _handoffTicksRemaining = 0;
        }

        if (_footprint != null) {
            boolean far = _target.getSquaredDistance(mod.getPlayer().getBlockPos()) > 64;
            boolean loaded = mod.getChunkTracker().isChunkLoaded(_target);
            PlacementRoute.SiteAction siteAction = PlacementRoute.siteAction(far, loaded,
                    _siteApproachStarted && _siteApproachTimer.elapsed());
            if (siteAction != PlacementRoute.SiteAction.SEARCH) {
                if (siteAction == PlacementRoute.SiteAction.FAIL) {
                    fail("Could not return to blueprint site at " + _target.toShortString());
                    return null;
                }
                if (!_siteApproachStarted) {
                    _siteApproachStarted = true;
                    _siteApproachTimer.reset();
                }
                releaseManualControl(mod);
                mod.getClientBaritone().getBuilderProcess().onLostControl();
                setDebugState("Returning to blueprint site");
                if (siteAction == PlacementRoute.SiteAction.WAIT) return null;
                if (_siteApproachTask == null) _siteApproachTask = new GetWithinRangeOfBlockTask(_target, 8);
                return _siteApproachTask;
            }
            _siteApproachStarted = false;
            _siteApproachTask = null;
            if (_recoveryRestartPending) {
                _recoveryRestartPending = false;
                return null;
            }
            Task recovery = recoverPlacement(mod);
            if (failureReason() != null) return null;
            if (recovery != null) {
                releaseManualControl(mod);
                mod.getClientBaritone().getBuilderProcess().onLostControl();
                _progressChecker.reset();
                return recovery;
            }
            if (_recoveryRestartPending) {
                _recoveryRestartPending = false;
                return null;
            }
            if (_recoveryStand != null || _recoveryWaitingForGround) return null;
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
        EventBus.unsubscribe(_placementInteraction);
        if (_footprint != null) mod.getBehaviour().pop();
        releaseManualControl(mod);
        mod.getClientBaritone().getBuilderProcess().onLostControl();
    }

    //TODO: Place structure where a leaf block was???? Might need to delete the block first if it's not empty/air/water.

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof PlaceBlockTask task) {
            return task._target.equals(_target) && task._useThrowaways == _useThrowaways && task._autoCollectStructureBlocks == _autoCollectStructureBlocks && java.util.Objects.equals(task._castOrigin, _castOrigin) && java.util.Objects.equals(task._footprint, _footprint) && Arrays.equals(task._toPlace, _toPlace);
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

    private boolean isPlaceableStack(Vibrato mod, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) return false;
        return _useThrowaways
                ? mod.getClientBaritoneSettings().acceptableThrowawayItems.value.contains(stack.getItem())
                    && !mod.getBehaviour().isProtected(stack.getItem())
                    && (_castOrigin == null || isStableCastMaterial(mod, stack.getItem(), _castOrigin))
                : Arrays.asList(_toPlace).contains(item.getBlock());
    }

    private ItemStack findPlaceableInventoryStack(Vibrato mod) {
        for (ItemStack stack : mod.getPlayer().getInventory().main) {
            if (isPlaceableStack(mod, stack)) return stack;
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

    /** A bounded attempt to obtain a genuinely reachable support-face vantage point. */
    private Task recoverPlacement(Vibrato mod) {
        if (_recoveryStand != null && _recoveryTimer.elapsed()) {
            abandonRecoveryStand("attempt deadline");
            return null;
        }
        if (_recoveryStand == null) {
            boolean grounded = mod.getPlayer().isOnGround()
                    && WorldHelper.isSolid(mod, mod.getPlayer().getBlockPos().down());
            PlacementRoute.GroundAction groundAction = PlacementRoute.groundAction(grounded,
                    _recoveryWaitingForGround, _recoveryWaitingForGround && _groundWaitTimer.elapsed());
            if (groundAction != PlacementRoute.GroundAction.PROCEED) {
                if (!_recoveryWaitingForGround) {
                    _recoveryWaitingForGround = true;
                    _groundWaitTimer.reset();
                } else if (groundAction == PlacementRoute.GroundAction.FAIL) {
                    _recoveryWaitingForGround = false;
                    fail("No supported placement route start at " + _target.toShortString());
                }
                releaseManualControl(mod);
                mod.getClientBaritone().getBuilderProcess().onLostControl();
                setDebugState("Waiting for supported footing");
                return null;
            }
            _recoveryWaitingForGround = false;
            if (!_directManualStarted) {
                if (tryManualPlace(mod)) {
                    _directManualStarted = true;
                    _directManualTimer.reset();
                    return null;
                }
            } else if (!_directManualTimer.elapsed() && tryManualPlace(mod)) {
                return null;
            }
            if (_recoveryAttempts >= 3) {
                fail("Exhausted placement positions at " + _target.toShortString());
                return null;
            }
            _recoveryAttempts++;
            _recoveryStand = findStand(mod, 0);
            for (int depth = 1; _recoveryStand == null && depth <= 2; depth++) {
                _recoveryStand = findStand(mod, depth);
                if (_recoveryStand != null) {
                    _scaffoldDepth = depth;
                    _scaffold = _recoveryStand.down(depth);
                    _scaffoldMaterialsReady = false;
                }
            }
            if (_recoveryStand == null) {
                fail("No reachable placement position at " + _target.toShortString());
                return null;
            }
            if (!registerRouteSupports(mod)) return null;
            if (_scaffold != null) _scaffoldMaterial = chooseScaffoldMaterial(hasLocalSand(mod, _scaffold))
                    == ScaffoldMaterial.SAND ? Items.SAND : Items.DIRT;
            Debug.logInternal("Placement recovery target=" + _target.toShortString() + " stand="
                    + _recoveryStand.toShortString() + " scaffold=" + _scaffold
                    + " depth=" + _scaffoldDepth + " material=" + _scaffoldMaterial
                    + " routeFloors=" + _selectedRoute.stream().map(BlockPos::down).toList());
            _recoveryTimer.reset();
        }
        if (_scaffold != null) {
            // Collection may have removed the foundation; never place a falling
            // block until the actual world still supports the entire column.
            if (!scaffoldFoundationIntact(_scaffold, block -> WorldHelper.isSolid(mod, block))) {
                abandonRecoveryStand("foundation removed");
                return null;
            }
            if (!_scaffoldMaterialsReady) {
                if (_recoveryChild != null && !_recoveryChild.isFinished(mod)) return _recoveryChild;
                if (mod.getItemStorage().getItemCountInventoryOnly(_scaffoldMaterial) < _scaffoldDepth) {
                    _recoveryChild = TaskCatalogue.getItemTask(_scaffoldMaterial, _scaffoldDepth);
                    setDebugState("Gathering scaffold blocks");
                    return _recoveryChild;
                }
                _scaffoldMaterialsReady = true;
                _recoveryChild = null;
            }
            for (int i = 0; i < _scaffoldDepth; i++) {
                BlockPos block = _scaffold.up(i);
                if (!WorldHelper.isSolid(mod, block)) {
                    if (!mod.getWorld().getBlockState(block).isReplaceable()
                            || !mod.getWorld().getFluidState(block).isEmpty()
                            || mod.getPlayer().getBoundingBox().intersects(new Box(block))) {
                        abandonRecoveryStand("scaffold cell obstructed, flooded, or occupied");
                        return null;
                    }
                    if (!(_recoveryChild instanceof PlaceBlockTask))
                        _recoveryChild = new PlaceBlockTask(block, Block.getBlockFromItem(_scaffoldMaterial));
                    setDebugState("Placing temporary scaffold " + (i + 1) + "/" + _scaffoldDepth);
                    return _recoveryChild;
                }
                if (mod.getWorld().getBlockState(block).getBlock() != Block.getBlockFromItem(_scaffoldMaterial)) {
                    abandonRecoveryStand("scaffold cell changed to a different block");
                    return null;
                }
                if (!registerSupport(mod, block)) return null;
                _recoveryChild = null;
            }
        }
        _recoveryChild = null;
        if (!mod.getPlayer().getBlockPos().equals(_recoveryStand)) {
            setDebugState("Approaching a reachable placement position");
            return new GetToBlockTask(_recoveryStand, false);
        }
        // The real interaction and block update, not arriving at the stand, prove placement.
        tryManualPlace(mod);
        return null;
    }

    private void abandonRecoveryStand(String reason) {
        _recoveryRestartPending = true;
        Debug.logInternal("Abandoning placement recovery target=" + _target.toShortString()
                + " stand=" + _recoveryStand + " reason=" + reason);
        if (_recoveryStand != null) _failedRecoveryStands.add(_recoveryStand);
        _recoveryStand = null;
        _scaffold = null;
        _scaffoldDepth = 0;
        _scaffoldMaterialsReady = false;
        _scaffoldMaterial = null;
        _selectedRoute = null;
        _recoveryChild = null;
    }

    private BlockPos findStand(Vibrato mod, int scaffoldDepth) {
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
            for (int y = -2; y <= 1; y++) {
                BlockPos stand = _target.add(x, y, z);
                BlockPos floor = stand.down();
                if (_failedRecoveryStands.contains(stand)
                        || _footprint.contains(stand) || _footprint.contains(stand.up())
                        || !mod.getWorld().getFluidState(stand).isEmpty()
                        || !mod.getWorld().getFluidState(stand.up()).isEmpty()
                        || !mod.getWorld().getBlockState(stand).getCollisionShape(mod.getWorld(), stand).isEmpty()
                        || !mod.getWorld().getBlockState(stand.up()).getCollisionShape(mod.getWorld(), stand.up()).isEmpty()) continue;
                if (scaffoldDepth > 0) {
                    BlockPos base = stand.down(scaffoldDepth);
                    if (!scaffoldSiteValid(base, scaffoldDepth,
                            block -> !_footprint.contains(block) && mod.getWorld().getBlockState(block).isReplaceable()
                                    && mod.getWorld().getFluidState(block).isEmpty()
                                    && !mod.getPlayer().getBoundingBox().intersects(new Box(block))
                                    && WorldHelper.canPlace(mod, block),
                            block -> WorldHelper.isSolid(mod, block))) continue;
                } else if (!WorldHelper.isSolid(mod, floor) || stand.equals(mod.getPlayer().getBlockPos())) continue;
                if (!standSeesSupport(mod, stand)) continue;
                Optional<List<BlockPos>> route = localRoute(mod, stand, scaffoldDepth);
                if (route.isEmpty()) continue;
                double distance = mod.getPlayer().getPos().squaredDistanceTo(Vec3d.ofBottomCenter(stand));
                if (distance < bestDistance) { bestDistance = distance; best = stand; _selectedRoute = route.get(); }
            }
        return best;
    }

    private Optional<List<BlockPos>> localRoute(Vibrato mod, BlockPos stand, int scaffoldDepth) {
        BlockPos base = stand.down(scaffoldDepth);
        Predicate<BlockPos> planned = pos -> scaffoldDepth > 0 && pos.getX() == base.getX()
                && pos.getZ() == base.getZ() && pos.getY() >= base.getY() && pos.getY() < stand.getY();
        Predicate<BlockPos> clear = pos -> mod.getChunkTracker().isChunkLoaded(pos) && !planned.test(pos)
                && mod.getWorld().getFluidState(pos).isEmpty()
                && mod.getWorld().getBlockState(pos).getCollisionShape(mod.getWorld(), pos).isEmpty();
        Predicate<BlockPos> solid = pos -> mod.getChunkTracker().isChunkLoaded(pos)
                && (planned.test(pos) || WorldHelper.isSolid(mod, pos));
        return PlacementRoute.find(mod.getPlayer().getBlockPos(), stand, clear, solid);
    }

    private boolean registerRouteSupports(Vibrato mod) {
        if (_selectedRoute != null) for (BlockPos pos : _selectedRoute) {
            BlockPos floor = pos.down();
            if (WorldHelper.isSolid(mod, floor) && !registerSupport(mod, floor)) return false;
        }
        return _scaffold == null || registerSupport(mod, _scaffold.down());
    }

    private boolean registerSupport(Vibrato mod, BlockPos pos) {
        if (!WorldHelper.isSolid(mod, pos)) return true;
        if (!_supportBlocks.containsKey(pos) && _supportBlocks.size() >= MAX_SUPPORT_BLOCKS) {
            fail("Blueprint support protection limit reached");
            return false;
        }
        _supportBlocks.put(pos.toImmutable(), mod.getWorld().getBlockState(pos).getBlock());
        return true;
    }

    private boolean hasLocalSand(Vibrato mod, BlockPos scaffoldBase) {
        if (mod.getItemStorage().getItemCountInventoryOnly(Items.SAND) >= _scaffoldDepth) return true;
        BlockPos player = mod.getPlayer().getBlockPos();
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) for (int y = -2; y <= 1; y++) {
            BlockPos pos = player.add(x, y, z);
            if (pos.equals(scaffoldBase.down()) || _footprint.contains(pos)
                    || !mod.getWorld().getBlockState(pos).isOf(Blocks.SAND)
                    || !WorldHelper.canBreak(mod, pos)) continue;
            if (LookHelper.getReach(pos).isPresent()) return true;
        }
        return false;
    }

    enum ScaffoldMaterial { SAND, DIRT }

    static ScaffoldMaterial chooseScaffoldMaterial(boolean localSand) {
        return localSand ? ScaffoldMaterial.SAND : ScaffoldMaterial.DIRT;
    }

    static boolean scaffoldFoundationIntact(BlockPos base, Predicate<BlockPos> solid) {
        return solid.test(base.down());
    }

    static boolean scaffoldSiteValid(BlockPos base, int depth, Predicate<BlockPos> clear, Predicate<BlockPos> solid) {
        if (depth < 1 || depth > 2 || !solid.test(base.down())) return false;
        for (int i = 0; i < depth; i++) if (!clear.test(base.up(i))) return false;
        return true;
    }

    private boolean standSeesSupport(Vibrato mod, BlockPos stand) {
        // Sneaking lowers the eye; using that height avoids selecting a stand
        // that only sees a top face while standing upright.
        Vec3d eye = Vec3d.ofBottomCenter(stand).add(0, 1.27, 0);
        double reach = mod.getClientBaritone().getPlayerContext().playerController().getBlockReachDistance();
        for (Direction direction : SUPPORT_ORDER) {
            BlockPos support = _target.offset(direction);
            Direction face = direction.getOpposite();
            if (!MovementHelper.canPlaceAgainst(mod.getClientBaritone().getPlayerContext(), support)) continue;
            Vec3d normal = Vec3d.of(face.getVector());
            Vec3d point = Vec3d.ofCenter(support).add(normal.multiply(0.5));
            if (!faceReachable(eye, point, normal, reach)) continue;
            BlockHitResult hit = mod.getWorld().raycast(new RaycastContext(eye,
                    point.subtract(normal.multiply(0.001)), RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, mod.getPlayer()));
            if (hitPlacesAt(hit, support, face, _target)) return true;
        }
        return false;
    }

    static boolean faceReachable(Vec3d eye, Vec3d point, Vec3d normal, double reach) {
        return eye.subtract(point).dotProduct(normal) > 0 && eye.squaredDistanceTo(point) <= reach * reach;
    }

    @Override
    protected boolean onChildFailure(Vibrato mod, Task child, String reason) {
        if (_recoveryStand != null) {
            abandonRecoveryStand("child failure: " + reason);
            return true;
        }
        return false;
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
                            && !_mod.getBehaviour().isProtected(possible.getBlock().asItem())
                            && (_castOrigin == null || isStableCastMaterial(_mod, possible.getBlock().asItem(), _castOrigin))) {
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
                // Keep the schematic solid if inventory changes during a builder
                // tick. The next task tick reports the missing material as failure.
                return Blocks.COBBLESTONE.getDefaultState();
            }
            // Don't care.
            return blockState;
        }
    }
}
