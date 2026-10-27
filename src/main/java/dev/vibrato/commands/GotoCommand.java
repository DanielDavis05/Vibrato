package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.*;
import dev.vibrato.tasks.movement.DefaultGoToDimensionTask;
import dev.vibrato.tasks.movement.GetToBlockTask;
import dev.vibrato.tasks.movement.GetToXZTask;
import dev.vibrato.tasks.movement.GetToYTask;
import dev.vibrato.tasksystem.Task;
import net.minecraft.util.math.BlockPos;

/**
 * Out of all the commands, this one probably demonstrates
 * why we need a better arg parsing system. Please.
 */
public class GotoCommand extends Command {

    public GotoCommand() throws CommandException {
        // x z
        // x y z
        // x y z dimension
        // (dimension)
        // (x z dimension)
        super("goto", "Tell bot to travel to a set of coordinates.",
                new Arg(GotoTarget.class, "[x y z dimension]/[x z dimension]/[y dimension]/[dimension]/[x y z]/[x z]/[y]")
        );
    }

    public static Task getMovementTaskFor(GotoTarget target) {
        return switch (target.getType()) {
            case XYZ -> new GetToBlockTask(new BlockPos(target.getX(), target.getY(), target.getZ()), target.getDimension());
            case XZ -> new GetToXZTask(target.getX(), target.getZ(), target.getDimension());
            case Y -> new GetToYTask(target.getY(), target.getDimension());
            case NONE -> new DefaultGoToDimensionTask(target.getDimension());
        };
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        GotoTarget target = parser.get(GotoTarget.class);
        mod.runUserTask(getMovementTaskFor(target), this::finish);
    }
}
