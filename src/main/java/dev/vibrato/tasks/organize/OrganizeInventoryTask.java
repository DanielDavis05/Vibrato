package dev.vibrato.tasks.organize;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.slot.ClickSlotTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.slots.Slot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Reads the main inventory once, then runs {@link InventoryPlan}'s hotbar swaps and junk throws one click at a time. */
public class OrganizeInventoryTask extends Task {

    private final Deque<InventoryPlan.Swap> _swaps = new ArrayDeque<>();
    private final Deque<Integer> _dumps = new ArrayDeque<>();
    private boolean _planned = false;
    private Task _current;

    @Override
    protected void onStart(Vibrato mod) {
        _planned = false;
        _swaps.clear();
        _dumps.clear();
        _current = null;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_current != null && _current.isActive() && !_current.isFinished(mod)) {
            return _current;
        }
        if (!_planned) {
            InventoryPlan.Plan plan = InventoryPlan.plan(readInventory(mod));
            _swaps.addAll(plan.swaps());
            _dumps.addAll(plan.dump());
            _planned = true;
        }
        if (!_swaps.isEmpty()) {
            InventoryPlan.Swap swap = _swaps.poll();
            setDebugState("Moving items to the hotbar");
            _current = new ClickSlotTask(Slot.getFromCurrentScreenInventory(swap.from()), swap.hotbar(), SlotActionType.SWAP);
            return _current;
        }
        if (!_dumps.isEmpty()) {
            int slot = _dumps.poll();
            setDebugState("Throwing away junk");
            _current = new ClickSlotTask(Slot.getFromCurrentScreenInventory(slot), 1, SlotActionType.THROW);
            return _current;
        }
        return null;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _planned && _swaps.isEmpty() && _dumps.isEmpty() && (_current == null || _current.isFinished(mod));
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof OrganizeInventoryTask;
    }

    @Override
    protected String toDebugString() {
        return "Organizing inventory";
    }

    private static List<InventoryPlan.Stack> readInventory(Vibrato mod) {
        PlayerInventory inventory = mod.getPlayer().getInventory();
        List<InventoryPlan.Stack> stacks = new ArrayList<>();
        for (int slot = 0; slot < InventoryPlan.MAIN_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty()) {
                String id = Registries.ITEM.getId(stack.getItem()).getPath();
                stacks.add(new InventoryPlan.Stack(slot, id, stack.getCount()));
            }
        }
        return stacks;
    }
}
