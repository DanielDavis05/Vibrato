package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.*;
import dev.vibrato.director.*;

/** One explicit Survival activity, with finite providers and a durable, verified outcome. */
public final class ActivityCommand extends Command {
    public ActivityCommand() throws CommandException {
        super("activity", "Run one seeded activity goal with verified action evidence", new Arg(String.class, "activity"));
    }
    @Override protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        String id = parser.get(String.class);
        ActivityRegistry registry = BuiltinActivities.createRegistry();
        if (!FocusedActivityTask.supportedIds().contains(id) || registry.get(id) == null)
            throw new CommandException("Unsupported focused activity: " + id + ". Proof-supported IDs: "
                    + String.join(", ", FocusedActivityTask.supportedIds().stream().sorted().toList()));
        long seed = mod.getFleetConfig().seed();
        mod.runUserTask(new FocusedActivityTask(registry, id, seed, Personas.forSeed(seed)), this::finish);
    }
}
