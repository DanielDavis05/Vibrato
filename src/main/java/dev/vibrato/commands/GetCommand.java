package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.commandsystem.*;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.ui.MessagePriority;
import dev.vibrato.util.ItemTarget;

public class GetCommand extends Command {

    public GetCommand() throws CommandException {
        super("get", "Get an item/resource",
                new Arg(ItemList.class, "items"));
    }

    private static void OnResourceDoesNotExist(Vibrato mod, String resource) {
        mod.log("\"" + resource + "\" is not a catalogued resource. Can't get it yet, sorry! If it's a generic block try using baritone.", MessagePriority.OPTIONAL);
        mod.log("Use @list to get a list of available resources.", MessagePriority.OPTIONAL);
    }

    private void GetItems(Vibrato mod, ItemTarget... items) {
        Task targetTask;
        if (items == null || items.length == 0) {
            mod.log("You must specify at least one item!");
            finish();
            return;
        }
        if (items.length == 1) {
            targetTask = TaskCatalogue.getItemTask(items[0]);
        } else {
            targetTask = TaskCatalogue.getSquashedItemTask(items);
        }
        if (targetTask != null) {
            mod.runUserTask(targetTask, this::finish);
        } else {
            finish();
        }
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        ItemList items = parser.get(ItemList.class);
        GetItems(mod, items.items);
    }
}