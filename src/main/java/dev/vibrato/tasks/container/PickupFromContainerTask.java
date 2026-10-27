package dev.vibrato.tasks.container;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.slot.ClickSlotTask;
import dev.vibrato.tasks.slot.EnsureFreeInventorySlotTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.trackers.storage.ContainerCache;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.slots.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class PickupFromContainerTask extends AbstractDoToStorageContainerTask {

    private final BlockPos _targetContainer;
    private final ItemTarget[] _targets;

    private final EnsureFreeInventorySlotTask _freeInventoryTask = new EnsureFreeInventorySlotTask();

    public PickupFromContainerTask(BlockPos targetContainer, ItemTarget... targets) {
        _targets = targets;
        _targetContainer = targetContainer;
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof PickupFromContainerTask task) {
            return Objects.equals(_targetContainer, task._targetContainer) && Arrays.equals(_targets, task._targets);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Picking up from container at (" + _targetContainer.toShortString() + "): " + Arrays.toString(_targets);
    }

    @Override
    protected Optional<BlockPos> getContainerTarget() {
        return Optional.of(_targetContainer);
    }

    @Override
    protected Task onTick(Vibrato mod) {
        // Free inventory while we're doing it.
        if (_freeInventoryTask.isActive() && !_freeInventoryTask.isFinished(mod) && !mod.getItemStorage().hasEmptyInventorySlot()) {
            setDebugState("Freeing inventory.");
            return _freeInventoryTask;
        }
        return super.onTick(mod);
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return Arrays.stream(_targets).allMatch(target -> mod.getItemStorage().getItemCountInventoryOnly(target.getMatches()) >= target.getTargetCount());
    }

    public static Optional<Slot> getBestSlotToTransfer(Vibrato mod, ItemTarget itemToMove, int currentItemQuantity, List<Slot> grabPotentials, Function<ItemStack, Boolean> canStackFit) {
        Slot bestPotential = null;
        int leftNeeded = itemToMove.getTargetCount() - currentItemQuantity;
        for (Slot slot : grabPotentials) {
            ItemStack stack = StorageHelper.getItemStackInSlot(slot);
            if (itemToMove.matches(stack.getItem())) {
                if (bestPotential == null) {
                    bestPotential = slot;
                    continue;
                }
                ItemStack currBest = StorageHelper.getItemStackInSlot(bestPotential);
                int overshoot = stack.getCount() - leftNeeded;
                int currBestOverhoot = currBest.getCount() - leftNeeded;
                boolean canFit = canStackFit.apply(stack);
                boolean currBestCanFit = canStackFit.apply(currBest);
                // Prioritize "fitting" in our inventory.
                if (canFit || !currBestCanFit) {
                    if (overshoot < 0) {
                        // Prioritize highest that goes under, then lowest that goes over.
                        if (currBestOverhoot > 0 || overshoot > currBestOverhoot)
                            bestPotential = slot;
                    } else if (overshoot > 0) {
                        // Prioritize the smaller overshoot.
                        if (overshoot < currBestOverhoot)
                            bestPotential = slot;
                    } else if (currBestOverhoot != 0) {
                        // We have a "perfect" fit.
                        bestPotential = slot;
                    }
                }
            }
        }
        return Optional.ofNullable(bestPotential);
    }

    @Override
    protected Task onContainerOpenSubtask(Vibrato mod, ContainerCache containerCache) {
        for (ItemTarget target : _targets) {
            // Go through each item
            int count = mod.getItemStorage().getItemCountInventoryOnly(target.getMatches());
            if (target.matches(StorageHelper.getItemStackInCursorSlot().getItem()))
                count -= StorageHelper.getItemStackInCursorSlot().getCount();
            if (count < target.getTargetCount()) {
                setDebugState("Collecting " + target);
                // Grab the item from the current chest that most closely matches our requirements
                List<Slot> potentials = mod.getItemStorage().getSlotsWithItemContainer(target.getMatches());

                // Pick the best slot to grab from.
                Optional<Slot> bestPotential = getBestSlotToTransfer(mod, target, count, potentials, stack -> mod.getItemStorage().getSlotThatCanFitInPlayerInventory(stack, false).isPresent());

                if (bestPotential.isPresent()) {
                    // Just pick it up, it's now ours.
                    return new ClickSlotTask(bestPotential.get());
                }
                setDebugState("SHOULD NOT HAPPEN! No valid items detected.");
            }
        }

        // We're done.
        setDebugState("Done");
        return null;
    }
}
