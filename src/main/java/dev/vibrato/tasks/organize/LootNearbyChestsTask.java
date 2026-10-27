package dev.vibrato.tasks.organize;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.container.LootContainerTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.ItemHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Opens chests and barrels within a radius, one at a time nearest first, and takes the valuable items from each. */
public class LootNearbyChestsTask extends Task {

    private static final Block[] CONTAINERS = {Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL};
    private static final int MAX_CONTAINERS = 12;
    private static final List<Item> VALUABLES = new ArrayList<>(List.of(
            Items.DIAMOND, Items.EMERALD, Items.IRON_INGOT, Items.GOLD_INGOT, Items.RAW_IRON, Items.RAW_GOLD,
            Items.LAPIS_LAZULI, Items.ENCHANTED_BOOK, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
            Items.NETHERITE_INGOT, Items.GOLDEN_CARROT, Items.COOKED_BEEF, Items.COOKED_PORKCHOP,
            Items.COOKED_CHICKEN, Items.COOKED_MUTTON, Items.BREAD));

    static {
        VALUABLES.addAll(Arrays.asList(ItemHelper.DIAMOND_TOOLS));
        VALUABLES.addAll(Arrays.asList(ItemHelper.IRON_TOOLS));
        VALUABLES.addAll(Arrays.asList(ItemHelper.DIAMOND_ARMORS));
        VALUABLES.addAll(Arrays.asList(ItemHelper.IRON_ARMORS));
    }

    private final int _radius;
    private final Set<BlockPos> _visited = new HashSet<>();
    private boolean _done = false;
    private Task _current;

    public LootNearbyChestsTask(int radius) {
        _radius = radius;
    }

    @Override
    protected void onStart(Vibrato mod) {
        mod.getBlockTracker().trackBlock(CONTAINERS);
        _visited.clear();
        _done = false;
        _current = null;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_current != null && _current.isActive() && !_current.isFinished(mod)) {
            return _current;
        }
        if (_visited.size() >= MAX_CONTAINERS) {
            _done = true;
            return null;
        }
        Vec3d here = mod.getPlayer().getPos();
        Optional<BlockPos> next = mod.getBlockTracker().getKnownLocations(CONTAINERS).stream()
                .filter(pos -> !_visited.contains(pos))
                .filter(pos -> pos.toCenterPos().distanceTo(here) <= _radius)
                .min(Comparator.comparingDouble(pos -> pos.toCenterPos().distanceTo(here)));
        if (next.isEmpty()) {
            _done = true;
            return null;
        }
        _visited.add(next.get());
        setDebugState("Looting container at " + next.get().toShortString());
        _current = new LootContainerTask(next.get(), VALUABLES);
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
        return other instanceof LootNearbyChestsTask task && task._radius == _radius;
    }

    @Override
    protected String toDebugString() {
        return "Looting chests within " + _radius + " blocks";
    }
}
