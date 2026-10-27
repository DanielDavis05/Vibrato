package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.*;
import dev.vibrato.tasks.container.StoreInStashTask;
import dev.vibrato.util.BlockRange;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.util.math.BlockPos;

public class StashCommand extends Command {
    public StashCommand() throws CommandException {
        // stash <stash_x> <stash_y> <stash_z> <stash_radius> [item list]
        super("stash", "Store an item in a chest/container stash. Will deposit ALL non-equipped items if item list is empty.",
                new Arg(Integer.class, "x_start"),
                new Arg(Integer.class, "y_start"),
                new Arg(Integer.class, "z_start"),
                new Arg(Integer.class, "x_end"),
                new Arg(Integer.class, "y_end"),
                new Arg(Integer.class, "z_end"),
                new Arg(ItemList.class, "items (empty for ALL)", null, 6, false));
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        BlockPos start = new BlockPos(
                parser.get(Integer.class),
                parser.get(Integer.class),
                parser.get(Integer.class)
        );
        BlockPos end = new BlockPos(
                parser.get(Integer.class),
                parser.get(Integer.class),
                parser.get(Integer.class)
        );

        ItemList itemList = parser.get(ItemList.class);
        ItemTarget[] items;
        if (itemList == null) {
            items = DepositCommand.getAllNonEquippedOrToolItemsAsTarget(mod);
        } else {
            items = itemList.items;
        }


        mod.runUserTask(new StoreInStashTask(true, new BlockRange(start, end, WorldHelper.getCurrentDimension()), items), this::finish);
    }
}
