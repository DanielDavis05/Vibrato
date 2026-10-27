package dev.vibrato.advancement;

import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import net.minecraft.util.Identifier;

/**
 * Runs the goal's command line through the command system. Finishes when that command finishes, or straight away if
 * the advancement is already done.
 */
public class PursueAdvancementTask extends Task {

    private final AdvancementGoal _goal;

    private boolean _started;
    private boolean _finished;

    public PursueAdvancementTask(AdvancementGoal goal) {
        _goal = goal;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _started = false;
        _finished = false;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_finished || _started) {
            return null;
        }
        _started = true;
        AdvancementTracker tracker = new AdvancementTracker(ClientAdvancements::doneIds);
        if (tracker.isDone(Identifier.of("minecraft", _goal.path()))) {
            Debug.logMessage("Advancement " + _goal.id() + " is already done");
            _finished = true;
            return null;
        }
        setDebugState("Running " + _goal.taskSpec());
        String prefix = mod.getModSettings().getCommandPrefix();
        Vibrato.getCommandExecutor().execute(prefix + _goal.taskSpec(), () -> _finished = true, error -> {
            Debug.logWarning("Advancement goal " + _goal.id() + " failed: " + error.getMessage());
            _finished = true;
        });
        return null;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _finished;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof PursueAdvancementTask task && task._goal.equals(_goal);
    }

    @Override
    protected String toDebugString() {
        return "Pursuing " + _goal.id();
    }
}
