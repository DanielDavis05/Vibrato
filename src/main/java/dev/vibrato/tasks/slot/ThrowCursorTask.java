package dev.vibrato.tasks.slot;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.slots.Slot;

public class ThrowCursorTask extends Task {

    private final Task _throwTask = new ClickSlotTask(Slot.UNDEFINED);

    @Override
    protected void onStart(Vibrato mod) {
    }

    @Override
    protected Task onTick(Vibrato mod) {
        return _throwTask;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task obj) {
        return obj instanceof ThrowCursorTask;
    }

    @Override
    protected String toDebugString() {
        return "Throwing Cursor";
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _throwTask.isFinished(mod);
    }
}
