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
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Removes the enchantments from one item in a grindstone, placing the grindstone if needed.
 */
public class GrindstoneTask extends DoStuffInContainerTask {

    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 2;

    private final Item _target;
    private final TimerGame _resultTimer = new TimerGame(3);
    private boolean _sawOutput = false;
    private boolean _failed = false;
    private boolean _done = false;

    public GrindstoneTask(Item target) {
        super(Blocks.GRINDSTONE, new ItemTarget(Items.GRINDSTONE, 1));
        _target = target;
    }

    /** True when the item had nothing to remove, or the grindstone produced no result. */
    public boolean failed() {
        return _failed;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_done) {
            return null;
        }
        if (!isContainerOpen(mod) && !mod.getItemStorage().hasItem(_target)) {
            return TaskCatalogue.getItemTask(new ItemTarget(_target, 1));
        }
        return super.onTick(mod);
    }

    @Override
    protected boolean isContainerOpen(Vibrato mod) {
        return mod.getPlayer().currentScreenHandler instanceof GrindstoneScreenHandler;
    }

    @Override
    protected Task containerSubTask(Vibrato mod) {
        ItemStack output = StorageHelper.getItemStackInSlot(new PlayerSlot(OUTPUT_SLOT));
        if (!output.isEmpty()) {
            _sawOutput = true;
            setDebugState("Taking ground item");
            return new ClickSlotTask(new PlayerSlot(OUTPUT_SLOT), SlotActionType.QUICK_MOVE);
        }
        if (_sawOutput) {
            _done = true;
            setDebugState("Ground item taken out");
            return null;
        }

        ItemStack input = StorageHelper.getItemStackInSlot(new PlayerSlot(INPUT_SLOT));
        if (!input.isOf(_target)) {
            if (!input.isEmpty()) {
                return new ClickSlotTask(new PlayerSlot(INPUT_SLOT), SlotActionType.QUICK_MOVE);
            }
            setDebugState("Placing " + _target.getTranslationKey() + " in grindstone");
            return new MoveItemToSlotFromInventoryTask(new ItemTarget(_target, 1), new PlayerSlot(INPUT_SLOT));
        }
        if (!input.hasEnchantments()) {
            // Nothing to remove, so the grindstone never produces a result.
            _failed = true;
            _done = true;
            StorageHelper.closeScreen();
            return null;
        }
        if (_resultTimer.elapsed()) {
            _failed = true;
            _done = true;
            StorageHelper.closeScreen();
            return null;
        }
        setDebugState("Waiting for grindstone result");
        return null;
    }

    @Override
    protected double getCostToMakeNew(Vibrato mod) {
        return 100;
    }

    @Override
    protected boolean isSubTaskEqual(DoStuffInContainerTask other) {
        return other instanceof GrindstoneTask task && task._target == _target;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _done;
    }

    @Override
    protected String toDebugString() {
        return "Disenchanting " + _target.getTranslationKey() + " in grindstone";
    }
}
