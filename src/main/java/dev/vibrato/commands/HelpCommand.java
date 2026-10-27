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
        mod.log("########## HELP: ##########", MessagePriority.OPTIONAL);
        int padSize = 10;
        for (Command c : mod.getCommandExecutor().allCommands()) {
            StringBuilder line = new StringBuilder();
            //line.append("");
            line.append(c.getName()).append(": ");
            int toAdd = padSize - c.getName().length();
            for (int i = 0; i < toAdd; ++i) {
                line.append(" ");
            }
            line.append(c.getDescription());
            mod.log(line.toString(), MessagePriority.OPTIONAL);
        }
        mod.log("###########################", MessagePriority.OPTIONAL);
        finish();
    }
}
