package dev.vibrato.tasks.movement;

import dev.vibrato.Vibrato;
import dev.vibrato.Playground;
import dev.vibrato.tasksystem.Task;

/**
 * Do nothing.
 */
public class IdleTask extends Task {
    @Override
    protected void onStart(Vibrato mod) {

    }

    @Override
    protected Task onTick(Vibrato mod) {
        // Do nothing except maybe test code
        Playground.IDLE_TEST_TICK_FUNCTION(mod);
        return null;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    public boolean isFinished(Vibrato mod) {
        // Never finish
        return false;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof IdleTask;
    }

    @Override
    protected String toDebugString() {
        return "Idle";
    }
}
