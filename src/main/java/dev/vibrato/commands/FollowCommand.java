package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.Arg;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.commandsystem.CommandException;
import dev.vibrato.tasks.movement.FollowPlayerTask;

public class FollowCommand extends Command {
    public FollowCommand() throws CommandException {
        super("follow", "Follows you or someone else", new Arg(String.class, "username", null, 0));
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        String username = parser.get(String.class);
        if (username == null) {
            if (mod.getButler().hasCurrentUser()) {
                username = mod.getButler().getCurrentUser();
            } else {
                mod.logWarning("No butler user currently present. Running this command with no user argument can ONLY be done via butler.");
                finish();
                return;
            }
        }
        mod.runUserTask(new FollowPlayerTask(username), this::finish);
    }
}