package dev.vibrato.tasksystem;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;
import dev.vibrato.tasks.movement.TimeoutWanderTask;

import java.util.function.Predicate;

public abstract class Task {

    private String _oldDebugState = "";
    private String _debugState = "";

    private Task _sub = null;

    private boolean _first = true;

    private boolean _stopped = false;

    private boolean _active = false;

    public void tick(Vibrato mod, TaskChain parentChain) {
        parentChain.addTaskToChain(this);
        if (_first) {
            Debug.logInternal("Task START: " + this);
            _active = true;
            onStart(mod);
            _first = false;
            _stopped = false;
        }
        if (_stopped) return;

        Task newSub = onTick(mod);
        // Debug state print
        if (!_oldDebugState.equals(_debugState)) {
            Debug.logInternal(toString());
            _oldDebugState = _debugState;
        }
        // We have a sub task
        if (newSub != null) {
            if (!newSub.isEqual(_sub)) {
                if (canBeInterrupted(mod, _sub, newSub)) {
                    // Our sub task is new
                    if (_sub != null) {
                        // Our previous sub must be interrupted.
                        _sub.stop(mod, newSub);
                    }

                    _sub = newSub;
                }
            }

            // Run our child
            _sub.tick(mod, parentChain);
        } else {
            // We are null
            if (_sub != null) {
                if (canBeInterrupted(mod, _sub, null)) {
                    _sub.stop(mod);
                    _sub = null;
                } else {
                    // The retained child must keep running so it can finish the protected action.
                    _sub.tick(mod, parentChain);
                }
            }
        }
    }

    public void reset() {
        _first = true;
        _active = false;
        _stopped = false;
    }

    public void stop(Vibrato mod) {
        stop(mod, null);
    }

    /**
     * Stops the task. Next time it's run it will run `onStart`
     */
    public void stop(Vibrato mod, Task interruptTask) {
        if (!_active) return;
        Debug.logInternal("Task STOP: " + this + ", interrupted by " + interruptTask);
        if (!_first) {
            onStop(mod, interruptTask);
        }

        if (_sub != null && !_sub.stopped()) {
            _sub.stop(mod, interruptTask);
        }

        _first = true;
        _active = false;
        _stopped = true;
    }

    /**
     * Lets the task know it's execution has been "suspended"
     *
     * STILL RUNS `onStop`
     *
     * Doesn't stop it all-together (meaning `isActive` still returns true)
     */
    public void interrupt(Vibrato mod, Task interruptTask) {
        if (!_active) return;
        if (!_first) {
            onStop(mod, interruptTask);
        }

        if (_sub != null && !_sub.stopped()) {
            _sub.interrupt(mod, interruptTask);
        }

        _first = true;
    }

    protected void setDebugState(String state) {
        if (state == null) {
            state = "";
        }
        _debugState = state;
    }

    // Virtual
    public boolean isFinished(Vibrato mod) {
        return false;
    }

    public boolean isActive() {
        return _active;
    }

    public boolean stopped() {
        return _stopped;
    }

    protected abstract void onStart(Vibrato mod);

    protected abstract Task onTick(Vibrato mod);

    // interruptTask = null if the task stopped cleanly
    protected abstract void onStop(Vibrato mod, Task interruptTask);

    protected abstract boolean isEqual(Task other);

    protected abstract String toDebugString();

    @Override
    public String toString() {
        return "<" + toDebugString() + "> " + _debugState;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Task task) {
            return isEqual(task);
        }
        return false;
    }

    public boolean thisOrChildSatisfies(Predicate<Task> pred) {
        Task t = this;
        while (t != null) {
            if (pred.test(t)) return true;
            t = t._sub;
        }
        return false;
    }

    public boolean thisOrChildAreTimedOut() {
        return thisOrChildSatisfies(task -> task instanceof TimeoutWanderTask);
    }

    /**
     * Sometimes a task just can NOT be bothered to be interrupted right now.
     * For instance, if we're in mid air and MUST complete the parkour movement.
     */
    private boolean canBeInterrupted(Vibrato mod, Task subTask, Task toInterruptWith) {
        if (subTask == null) return true;
        return !subTask.thisOrChildSatisfies(task ->
                task instanceof ITaskCanForce canForce && canForce.shouldForce(mod, toInterruptWith));
    }
}
