package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.Arg;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.commandsystem.CommandException;
import dev.vibrato.ui.MessagePriority;

public class HelpCommand extends Command {
    public HelpCommand() throws CommandException {
        super("help", "Find commands or show usage for one command", new Arg(String.class, "command or all", null, 0));
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        String query = parser.get(String.class);
        String prefix = mod.getModSettings().getCommandPrefix();
        if (query == null) {
            mod.log("Vibrato commands · Type " + prefix + " and press Tab to explore", MessagePriority.OPTIONAL);
            mod.log("Collect  " + prefix + "get · " + prefix + "equip · " + prefix + "food", MessagePriority.OPTIONAL);
            mod.log("Move     " + prefix + "goto · " + prefix + "follow · " + prefix + "coords", MessagePriority.OPTIONAL);
            mod.log("Manage  " + prefix + "inventory · " + prefix + "deposit · " + prefix + "status · " + prefix + "stop", MessagePriority.OPTIONAL);
            mod.log("Use " + prefix + "help <command> for details or " + prefix + "help all for every command. Baritone uses #.", MessagePriority.OPTIONAL);
        } else if (query.equalsIgnoreCase("all")) {
            mod.log("All Vibrato commands · Select a command to put it in chat", MessagePriority.OPTIONAL);
            mod.getCommandExecutor().allCommands().stream()
                    .sorted((a, b) -> a.getName().compareTo(b.getName()))
                    .forEach(command -> mod.log(prefix + command.getHelpRepresentation() + " — " + command.getDescription(), MessagePriority.OPTIONAL));
        } else {
            String name = query.startsWith(prefix) ? query.substring(prefix.length()) : query;
            Command command = mod.getCommandExecutor().get(name);
            if (command == null) {
                mod.log("No command named " + query + ". Try " + prefix + "help all.", MessagePriority.OPTIONAL);
            } else {
                mod.log(prefix + command.getHelpRepresentation() + " — " + command.getDescription(), MessagePriority.OPTIONAL);
            }
        }
        finish();
    }
}
