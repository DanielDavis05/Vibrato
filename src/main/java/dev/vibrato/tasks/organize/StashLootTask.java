package dev.vibrato.tasks.organize;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.construction.PlaceBlockNearbyTask;
import dev.vibrato.tasks.container.StoreInAnyContainerTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Moves loot that is not needed for tools, food or building into a storage chest. Uses the nearest tracked chest or
 * barrel within {@link #CHEST_RANGE} blocks, and places a chest when there is none.
 */
public class StashLootTask extends Task {

    private static final Block[] CONTAINERS = {Blocks.CHEST, Blocks.BARREL};
    private static final double CHEST_RANGE = 30;
    private static final int MAX_STORE_ROUNDS = 6;

    private final int _minValueItems;
    private int _storeRounds = 0;
    private boolean _chestPlaced = false;
    private boolean _done = false;
    private Task _current;

    public StashLootTask(int minValueItems) {
        _minValueItems = minValueItems;
    }

    @Override
    protected void onStart(Vibrato mod) {
        mod.getBlockTracker().trackBlock(CONTAINERS);
        _storeRounds = 0;
        _chestPlaced = false;
        _done = false;
        _current = null;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_current != null && _current.isActive() && !_current.isFinished(mod)) {
            return _current;
        }
        Map<Item, Integer> loot = stashableLoot(mod);
        int total = loot.values().stream().mapToInt(Integer::intValue).sum();
        if (total < _minValueItems || _storeRounds >= MAX_STORE_ROUNDS) {
            _done = true;
            return null;
        }
        Vec3d here = mod.getPlayer().getPos();
        boolean containerNear = mod.getBlockTracker().getNearestWithinRange(here, CHEST_RANGE, CONTAINERS).isPresent();
        if (!containerNear && !_chestPlaced) {
            if (!mod.getItemStorage().hasItem(Items.CHEST)) {
                setDebugState("Getting a chest");
                _current = TaskCatalogue.getItemTask(Items.CHEST, 1);
                return _current;
            }
            _chestPlaced = true;
            setDebugState("Placing a chest");
            _current = new PlaceBlockNearbyTask(pos -> WorldHelper.isAir(mod, pos.up()) || WorldHelper.canBreak(mod, pos.up()), Blocks.CHEST);
            return _current;
        }
        _storeRounds++;
        ItemTarget[] targets = loot.entrySet().stream()
                .map(entry -> new ItemTarget(entry.getKey(), entry.getValue()))
                .toArray(ItemTarget[]::new);
        setDebugState("Stashing " + total + " items");
        _current = new StoreInAnyContainerTask(false, targets);
        return _current;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _done;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(CONTAINERS);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof StashLootTask task && task._minValueItems == _minValueItems;
    }

    @Override
    protected String toDebugString() {
        return "Stashing loot (at least " + _minValueItems + " items)";
    }

    /** Non-essential stacks in the main inventory (hotbar excluded), summed per item. */
    private static Map<Item, Integer> stashableLoot(Vibrato mod) {
        Map<Item, Integer> loot = new LinkedHashMap<>();
        for (int slot = InventoryPlan.HOTBAR_SIZE; slot < InventoryPlan.MAIN_SIZE; slot++) {
            ItemStack stack = mod.getPlayer().getInventory().getStack(slot);
            if (!stack.isEmpty() && InventoryPlan.isStashable(Registries.ITEM.getId(stack.getItem()).getPath())) {
                loot.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return loot;
    }
}
