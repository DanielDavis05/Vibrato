package dev.vibrato.tasks.life;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;

/**
 * Stands in for a life activity whose parameters could not be turned into a task. It finishes on its first tick, so
 * the planner moves on instead of the client crashing.
 */
public class UnsupportedLifeTask extends Task {

    private final String _reason;

    public UnsupportedLifeTask(String reason) {
        _reason = reason;
    }

    public boolean failed() {
        return true;
    }

    @Override
    protected void onStart(Vibrato mod) {
    }

    @Override
    protected Task onTick(Vibrato mod) {
        return null;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return true;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof UnsupportedLifeTask task && task._reason.equals(_reason);
    }

    @Override
    protected String toDebugString() {
        return "Unsupported life step: " + _reason;
    }
}
