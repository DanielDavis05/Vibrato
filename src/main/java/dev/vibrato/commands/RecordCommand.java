package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.Arg;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.commandsystem.CommandException;
import dev.vibrato.human.record.MotionRecorder;

import java.io.IOException;

/** Records motion traces to run/traces for the human motion fitting scripts. */
public final class RecordCommand extends Command {

    public RecordCommand() throws CommandException {
        super("record", "Record a motion trace: start <name> [human|bot], or stop",
                new Arg(String.class, "start|stop"),
                new Arg(String.class, "name"),
                new Arg(String.class, "human|bot", "human", 2));
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        String verb = parser.get(String.class);
        if (verb.equals("stop")) {
            MotionRecorder.stop();
            finish();
            return;
        }
        if (!verb.equals("start")) {
            throw new CommandException("Expected 'start' or 'stop', got '" + verb + "'.");
        }
        String name = parser.get(String.class);
        String source = parser.get(String.class);
        try {
            MotionRecorder.start(mod, name, source);
        } catch (IOException | IllegalArgumentException e) {
            throw new CommandException("Could not start recording: " + e.getMessage());
        }
        finish();
    }
}
