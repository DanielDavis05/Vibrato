package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.ui.MessagePriority;

public class HelpCommand extends Command {

    public HelpCommand() {
        super("help", "Lists all commands");
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) {
        String prefix = mod.getModSettings().getCommandPrefix();
        mod.log("Vibrato commands · Type " + prefix + " and press Tab to explore", MessagePriority.OPTIONAL);
        mod.getCommandExecutor().allCommands().stream()
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .forEach(command -> mod.log(prefix + command.getHelpRepresentation() + " — " + command.getDescription(), MessagePriority.OPTIONAL));
        mod.log("Chain commands with ; · Baritone uses #", MessagePriority.OPTIONAL);
        finish();
    }
}
