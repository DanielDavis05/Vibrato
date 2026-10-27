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
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Enchants one unenchanted item at an enchanting table, placing the table if needed. Picks the most expensive option
 * whose enchantment power is at least {@code minLevel}, and fails if no option qualifies.
 */
public class EnchantItemTask extends DoStuffInContainerTask {

    private static final int ITEM_SLOT = 0;
    private static final int LAPIS_SLOT = 1;
    private static final int[] OPTION_COSTS = {1, 2, 3};
    private static final int LAPIS_NEEDED = 3;
    private static final int XP_NEEDED = 3;
    private static final int MAX_CLICKS = 6;

    private final Item _target;
    private final int _minLevel;
    private final TimerGame _clickTimer = new TimerGame(0.5);
    private int _clicks = 0;
    private boolean _enchanted = false;
    private boolean _failed = false;
    private boolean _done = false;

    public EnchantItemTask(Item target, int minLevel) {
        super(Blocks.ENCHANTING_TABLE, new ItemTarget(Items.ENCHANTING_TABLE, 1));
        _target = target;
        _minLevel = minLevel;
    }

    /** True when the table offered no option with enough power, or enchanting kept failing. */
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
        if (mod.getItemStorage().getItemCount(Items.LAPIS_LAZULI) < LAPIS_NEEDED) {
            return TaskCatalogue.getItemTask(Items.LAPIS_LAZULI, LAPIS_NEEDED);
        }
        if (mod.getPlayer().experienceLevel < XP_NEEDED) {
            return new CollectXpTask(XP_NEEDED);
        }
        return super.onTick(mod);
    }

    @Override
    protected boolean isContainerOpen(Vibrato mod) {
        return mod.getPlayer().currentScreenHandler instanceof EnchantmentScreenHandler;
    }

    @Override
    protected Task containerSubTask(Vibrato mod) {
        EnchantmentScreenHandler handler = (EnchantmentScreenHandler) mod.getPlayer().currentScreenHandler;
        ItemStack item = StorageHelper.getItemStackInSlot(new PlayerSlot(ITEM_SLOT));

        if (_enchanted) {
            if (item.isEmpty()) {
                _done = true;
                setDebugState("Enchanted and taken out");
                return null;
            }
            setDebugState("Taking enchanted item out");
            return new ClickSlotTask(new PlayerSlot(ITEM_SLOT), SlotActionType.QUICK_MOVE);
        }
        if (isEnchanted(item)) {
            _enchanted = true;
            return new ClickSlotTask(new PlayerSlot(ITEM_SLOT), SlotActionType.QUICK_MOVE);
        }
        if (!item.isOf(_target)) {
            if (!item.isEmpty()) {
                return new ClickSlotTask(new PlayerSlot(ITEM_SLOT), SlotActionType.QUICK_MOVE);
            }
            setDebugState("Placing " + _target.getTranslationKey() + " in table");
            return new MoveItemToSlotFromInventoryTask(new ItemTarget(_target, 1), new PlayerSlot(ITEM_SLOT));
        }
        if (handler.getLapisCount() < LAPIS_NEEDED) {
            setDebugState("Placing lapis in table");
            return new MoveItemToSlotFromInventoryTask(new ItemTarget(Items.LAPIS_LAZULI, LAPIS_NEEDED), new PlayerSlot(LAPIS_SLOT));
        }

        int index = EnchantOptionChooser.choose(handler.enchantmentPower, OPTION_COSTS,
                mod.getPlayer().experienceLevel, handler.getLapisCount(), _minLevel);
        if (index < 0) {
            setDebugState("No table option reaches level " + _minLevel);
            fail();
            return null;
        }
        if (!_clickTimer.elapsed()) {
            setDebugState("Waiting for enchant result");
            return null;
        }
        if (++_clicks > MAX_CLICKS) {
            setDebugState("Enchanting keeps failing");
            fail();
            return null;
        }
        _clickTimer.reset();
        mod.getController().clickButton(handler.syncId, index);
        setDebugState("Enchanting with option " + index);
        return null;
    }

    private static boolean isEnchanted(ItemStack stack) {
        return stack.hasEnchantments() || stack.isOf(Items.ENCHANTED_BOOK);
    }

    private void fail() {
        _failed = true;
        _done = true;
        StorageHelper.closeScreen();
    }

    @Override
    protected double getCostToMakeNew(Vibrato mod) {
        return 100;
    }

    @Override
    protected boolean isSubTaskEqual(DoStuffInContainerTask other) {
        return other instanceof EnchantItemTask task && task._target == _target && task._minLevel == _minLevel;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _done;
    }

    @Override
    protected String toDebugString() {
        return "Enchanting " + _target.getTranslationKey() + " to power " + _minLevel;
    }
}
