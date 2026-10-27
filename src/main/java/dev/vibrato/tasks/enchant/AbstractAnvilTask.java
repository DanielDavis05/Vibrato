package dev.vibrato.tasks.enchant;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.container.DoStuffInContainerTask;
import dev.vibrato.tasks.slot.ClickSlotTask;
import dev.vibrato.tasks.slot.MoveItemToSlotFromInventoryTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.slots.PlayerSlot;
import dev.vibrato.util.time.TimerGame;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Shared anvil flow: put the target in the first slot and an optional second item in the second, then take the
 * result. Collects the XP the anvil costs when the player is short of levels.
 */
public abstract class AbstractAnvilTask extends DoStuffInContainerTask {

    private static final int TARGET_SLOT = 0;
    private static final int SECOND_SLOT = 1;
    private static final int OUTPUT_SLOT = 2;
    private static final Block[] ANVILS = {Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL};

    protected final Item _target;
    protected final Item _second;
    private final TimerGame _resultTimer = new TimerGame(3);
    private boolean _inputsPlaced = false;
    private boolean _sawOutput = false;
    private boolean _failed = false;
    private boolean _done = false;

    protected AbstractAnvilTask(Item target, Item second) {
        super(ANVILS, new ItemTarget(Items.ANVIL, 1));
        _target = target;
        _second = second;
    }

    /** Hook for subtasks that must configure the anvil once the target is placed (e.g. rename). */
    protected void onTargetPlaced(AnvilScreenHandler handler) {

    }

    /** Extra identity compared by {@link #isEqual}. */
    protected abstract boolean isSameAnvilJob(AbstractAnvilTask other);

    public boolean failed() {
        return _failed;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_done) {
            return null;
        }
        if (isContainerOpen(mod)) {
            return super.onTick(mod);
        }
        if (!mod.getItemStorage().hasItem(_target)) {
            return TaskCatalogue.getItemTask(new ItemTarget(_target, 1));
        }
        if (_second != null && !mod.getItemStorage().hasItem(_second)) {
            return TaskCatalogue.getItemTask(new ItemTarget(_second, 1));
        }
        return super.onTick(mod);
    }

    @Override
    protected boolean isContainerOpen(Vibrato mod) {
        return mod.getPlayer().currentScreenHandler instanceof AnvilScreenHandler;
    }

    @Override
    protected Task containerSubTask(Vibrato mod) {
        AnvilScreenHandler handler = (AnvilScreenHandler) mod.getPlayer().currentScreenHandler;
        ItemStack output = StorageHelper.getItemStackInSlot(new PlayerSlot(OUTPUT_SLOT));

        if (!output.isEmpty()) {
            _sawOutput = true;
            int cost = handler.getLevelCost();
            if (mod.getPlayer().experienceLevel < cost) {
                setDebugState("Collecting " + cost + " XP levels for the anvil");
                StorageHelper.closeScreen();
                return new CollectXpTask(cost);
            }
            setDebugState("Taking anvil result");
            return new ClickSlotTask(new PlayerSlot(OUTPUT_SLOT), SlotActionType.QUICK_MOVE);
        }
        if (_sawOutput) {
            _done = true;
            setDebugState("Anvil result taken");
            return null;
        }

        ItemStack target = StorageHelper.getItemStackInSlot(new PlayerSlot(TARGET_SLOT));
        if (!target.isOf(_target)) {
            if (!target.isEmpty()) {
                return new ClickSlotTask(new PlayerSlot(TARGET_SLOT), SlotActionType.QUICK_MOVE);
            }
            setDebugState("Placing " + _target.getTranslationKey() + " in anvil");
            return new MoveItemToSlotFromInventoryTask(new ItemTarget(_target, 1), new PlayerSlot(TARGET_SLOT));
        }
        onTargetPlaced(handler);

        if (_second != null) {
            ItemStack second = StorageHelper.getItemStackInSlot(new PlayerSlot(SECOND_SLOT));
            if (!second.isOf(_second)) {
                if (!second.isEmpty()) {
                    return new ClickSlotTask(new PlayerSlot(SECOND_SLOT), SlotActionType.QUICK_MOVE);
                }
                setDebugState("Placing " + _second.getTranslationKey() + " in anvil");
                return new MoveItemToSlotFromInventoryTask(new ItemTarget(_second, 1), new PlayerSlot(SECOND_SLOT));
            }
        }

        // Both inputs are in place and the anvil still shows no result: give it a moment, then give up.
        if (!_inputsPlaced) {
            _inputsPlaced = true;
            _resultTimer.reset();
        }
        if (_resultTimer.elapsed()) {
            _failed = true;
            _done = true;
            StorageHelper.closeScreen();
            return null;
        }
        setDebugState("Waiting for anvil result");
        return null;
    }

    @Override
    protected double getCostToMakeNew(Vibrato mod) {
        return 100;
    }

    @Override
    protected boolean isSubTaskEqual(DoStuffInContainerTask other) {
        return other instanceof AbstractAnvilTask task && task.getClass() == getClass()
                && task._target == _target && task._second == _second && isSameAnvilJob(task);
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _done;
    }
}
