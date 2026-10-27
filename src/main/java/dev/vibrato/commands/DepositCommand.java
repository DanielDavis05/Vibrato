package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.*;
import dev.vibrato.tasks.container.StoreInAnyContainerTask;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.slots.PlayerSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolItem;
import org.apache.commons.lang3.ArrayUtils;

public class DepositCommand extends Command {
    public DepositCommand() throws CommandException {
        super("deposit", "Deposit ALL of our items", new Arg(ItemList.class, "items (empty for ALL non gear items)", null, 0, false));
    }

    public static ItemTarget[] getAllNonEquippedOrToolItemsAsTarget(Vibrato mod) {
        return StorageHelper.getAllInventoryItemsAsTargets(slot -> {
            // Ignore armor
            if (ArrayUtils.contains(PlayerSlot.ARMOR_SLOTS, slot))
                return false;
            ItemStack stack = StorageHelper.getItemStackInSlot(slot);
            // Ignore tools
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                return !(item instanceof ToolItem);
            }
            return false;
        });
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        ItemList itemList = parser.get(ItemList.class);
        ItemTarget[] items;
        if (itemList == null) {
            items = getAllNonEquippedOrToolItemsAsTarget(mod);
        } else {
            items = itemList.items;
        }

        mod.runUserTask(new StoreInAnyContainerTask(false, items), this::finish);
    }
}
