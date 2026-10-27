package dev.vibrato.tasks.container;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.InteractWithBlockTask;
import dev.vibrato.tasks.slot.ClickSlotTask;
import dev.vibrato.tasks.slot.EnsureFreeInventorySlotTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.trackers.storage.ContainerType;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.slots.Slot;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;


public class LootContainerTask extends Task {
    private boolean _weDoneHere = false;
    private final Predicate<ItemStack> _check;
    public final BlockPos chest;
    public final List<Item> targets = new ArrayList<>();

    public LootContainerTask(BlockPos chestPos, List<Item> items) {
        chest = chestPos;
        targets.addAll(items);
        _check = x -> true;
    }

    public LootContainerTask(BlockPos chestPos, List<Item> items, Predicate<ItemStack> pred) {
        chest = chestPos;
        targets.addAll(items);
        _check = pred;
    }

    @Override
    protected void onStart(Vibrato mod) {
        mod.getBehaviour().push();
        for (Item item : targets) {
            if (!mod.getBehaviour().isProtected(item)) {
                mod.getBehaviour().addProtectedItems(item);
            }
        }
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if(!ContainerType.screenHandlerMatches(ContainerType.CHEST)) {
            setDebugState("Interact with container");
            return new InteractWithBlockTask(chest);
        }
        ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
        if (!cursor.isEmpty()) {
            Optional<Slot> toFit = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursor, false).or(() -> StorageHelper.getGarbageSlot(mod));
            if (toFit.isPresent()) {
                setDebugState("Putting cursor in inventory");
                return new ClickSlotTask(toFit.get());
            } else {
                setDebugState("Ensuring space");
                return new EnsureFreeInventorySlotTask();
            }
        }
        Optional<Slot> optimal = getAMatchingSlot(mod);
        if (optimal.isEmpty()) {
            _weDoneHere = true;
            return null;
        }
        setDebugState("Looting items: " + targets);
        return new ClickSlotTask(optimal.get());
    }

    @Override
    protected void onStop(Vibrato mod, Task task) {
        StorageHelper.closeScreen();
        mod.getBehaviour().pop();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof LootContainerTask && targets == ((LootContainerTask) other).targets;
    }

    private Optional<Slot> getAMatchingSlot(Vibrato mod) {
        for (Item item : targets) {
            List<Slot> slots = mod.getItemStorage().getSlotsWithItemContainer(item);
            if (!slots.isEmpty()) for (Slot slot : slots) {
                if (_check.test(StorageHelper.getItemStackInSlot(slot))) return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _weDoneHere || (ContainerType.screenHandlerMatchesAny() &&
                getAMatchingSlot(mod).isEmpty());
    }

    @Override
    protected String toDebugString() {
        return "Looting a container";
    }
}
