package dev.vibrato.tasks.movement;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;
import dev.vibrato.tasks.AbstractDoToClosestObjectTask;
import dev.vibrato.tasks.resources.SatisfyMiningRequirementTask;
import dev.vibrato.tasks.slot.EnsureFreeInventorySlotTask;
import dev.vibrato.tasksystem.ITaskRequiresGrounded;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.MiningRequirement;
import dev.vibrato.util.helpers.StlHelper;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.helpers.WorldHelper;
import dev.vibrato.util.progresscheck.MovementProgressChecker;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class PickupDroppedItemTask extends AbstractDoToClosestObjectTask<ItemEntity> implements ITaskRequiresGrounded {

    // Am starting to regret not making this a singleton
    private Vibrato _mod;

    private static final Task getPickaxeFirstTask = new SatisfyMiningRequirementTask(MiningRequirement.STONE);
    // Not clean practice, but it helps keep things self contained I think.
    private static boolean isGettingPickaxeFirstFlag = false;
    private final ItemTarget[] _itemTargets;
    private final Set<ItemEntity> _blacklist = new HashSet<>();
    private final MovementProgressChecker _progressChecker = new MovementProgressChecker(3);
    private final TimeoutWanderTask _wanderTask = new TimeoutWanderTask(20);
    private final boolean _freeInventoryIfFull;
    private boolean _collectingPickaxeForThisResource = false;
    private ItemEntity _currentDrop = null;

    public PickupDroppedItemTask(ItemTarget[] itemTargets, boolean freeInventoryIfFull) {
        _itemTargets = itemTargets;
        _freeInventoryIfFull = freeInventoryIfFull;
    }

    public PickupDroppedItemTask(ItemTarget target, boolean freeInventoryIfFull) {
        this(new ItemTarget[]{target}, freeInventoryIfFull);
    }

    public PickupDroppedItemTask(Item item, int targetCount, boolean freeInventoryIfFull) {
        this(new ItemTarget(item, targetCount), freeInventoryIfFull);
    }
    public PickupDroppedItemTask(Item item, int targetCount) {
        this(item, targetCount, true);
    }

    public static boolean isIsGettingPickaxeFirst(Vibrato mod) {
        return isGettingPickaxeFirstFlag && mod.getModSettings().shouldCollectPickaxeFirst();
    }

    public boolean isCollectingPickaxeForThis() {
        return _collectingPickaxeForThisResource;
    }

    @Override
    protected void onStart(Vibrato mod) {

    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected Task onTick(Vibrato mod) {
        _mod = mod;
        if (_wanderTask.isActive() && !_wanderTask.isFinished(mod)) {
            setDebugState("Wandering after blacklisting item...");
            _progressChecker.reset();
            return _wanderTask;
        }

        // If we're getting a pickaxe for THIS resource...
        if (isIsGettingPickaxeFirst(mod) && _collectingPickaxeForThisResource && !StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.STONE)) {
            _progressChecker.reset();
            setDebugState("Collecting pickaxe first");
            return getPickaxeFirstTask;
        } else {
            if (StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.STONE)) {
                isGettingPickaxeFirstFlag = false;
            }
            _collectingPickaxeForThisResource = false;
        }

        if (!_progressChecker.check(mod)) {
            _progressChecker.reset();
            if (_currentDrop != null && !_currentDrop.getStack().isEmpty()) {
                // We might want to get a pickaxe first.
                if (!isGettingPickaxeFirstFlag && mod.getModSettings().shouldCollectPickaxeFirst() && !StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.STONE)) {
                    Debug.logMessage("Failed to pick up drop, will try to collect a stone pickaxe first and try again!");
                    _collectingPickaxeForThisResource = true;
                    isGettingPickaxeFirstFlag = true;
                    return getPickaxeFirstTask;
                }

                Debug.logInternal(StlHelper.toString(_blacklist, element -> element == null ? "(null)" : element.getStack().getItem().getTranslationKey()));
                Debug.logMessage("Failed to pick up drop, suggesting it's unreachable.");
                _blacklist.add(_currentDrop);
                mod.getEntityTracker().requestEntityUnreachable(_currentDrop);
                return _wanderTask;
            }
        }

        return super.onTick(mod);
    }


    @Override
    protected boolean isEqual(Task other) {
        // Same target items
        if (other instanceof PickupDroppedItemTask task) {
            return Arrays.equals(task._itemTargets, _itemTargets) && task._freeInventoryIfFull == _freeInventoryIfFull;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        StringBuilder result = new StringBuilder();
        result.append("Pickup Dropped Items: [");
        int c = 0;
        for (ItemTarget target : _itemTargets) {
            result.append(target.toString());
            if (++c != _itemTargets.length) {
                result.append(", ");
            }
        }
        result.append("]");
        return result.toString();
    }

    @Override
    protected Vec3d getPos(Vibrato mod, ItemEntity obj) {
        if (!obj.isOnGround() && !obj.isTouchingWater()) {
            // Assume we'll land down one or two blocks from here. We could do this more advanced but whatever.
            BlockPos p = obj.getBlockPos();
            if (!WorldHelper.isSolid(mod, p.down(3))) {
                return obj.getPos().subtract(0,2,0);
            }
            return obj.getPos().subtract(0,1,0);
        }
        return obj.getPos();
    }

    @Override
    protected Optional<ItemEntity> getClosestTo(Vibrato mod, Vec3d pos) {
        return mod.getEntityTracker().getClosestItemDrop(
                pos,
                _itemTargets);
    }

    @Override
    protected Vec3d getOriginPos(Vibrato mod) {
        return mod.getPlayer().getPos();
    }

    @Override
    protected Task getGoalTask(ItemEntity itemEntity) {
        if (!itemEntity.equals(_currentDrop)) {
            _currentDrop = itemEntity;
            _progressChecker.reset();
            if (isGettingPickaxeFirstFlag && _collectingPickaxeForThisResource) {
                Debug.logMessage("New goal, no longer collecting a pickaxe.");
                _collectingPickaxeForThisResource = false;
                isGettingPickaxeFirstFlag = false;
            }
        }
        // Ensure our inventory is free if we're close
        boolean touching = _mod.getEntityTracker().isCollidingWithPlayer(itemEntity);
        if (touching) {
            if (_freeInventoryIfFull) {
                if (_mod.getItemStorage().getSlotsThatCanFitInPlayerInventory(itemEntity.getStack(), false).isEmpty()) {
                    return new EnsureFreeInventorySlotTask();
                }
            }
        }
        return new GetToEntityTask(itemEntity);
    }

    @Override
    protected boolean isValid(Vibrato mod, ItemEntity obj) {
        return obj.isAlive() && !_blacklist.contains(obj);
    }

}
