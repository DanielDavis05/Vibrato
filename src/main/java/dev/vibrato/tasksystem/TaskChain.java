package dev.vibrato.tasksystem;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;

import java.util.ArrayList;
import java.util.List;

public abstract class TaskChain {

    private final List<Task> _cachedTaskChain = new ArrayList<>();
    private final List<Task> _activeTicks = new ArrayList<>();
    private static final int MAX_TASK_DEPTH = 128;

    // The displayed chain persists after a tick; recursion checks must instead
    // use only the current call stack, which is unwound even on exceptions.
    boolean canEnter(Task candidate) {
        if (_activeTicks.size() >= MAX_TASK_DEPTH) return false;
        for (Task ancestor : _activeTicks) {
            if (ancestor == candidate || (ancestor.getClass() == candidate.getClass()
                    && ancestor.equals(candidate) && candidate.equals(ancestor))) return false;
        }
        return true;
    }

    void enter(Task task) { _activeTicks.add(task); }
    void leave() { _activeTicks.remove(_activeTicks.size() - 1); }

    void reportCycle(Task candidate) {
        Debug.logWarning("Task cycle or depth limit (" + MAX_TASK_DEPTH + ") blocked: "
                + candidate + " in " + _activeTicks);
    }

    public TaskChain(TaskRunner runner) {
        runner.addTaskChain(this);
    }

    public void tick(Vibrato mod) {
        _cachedTaskChain.clear();
        onTick(mod);
    }

    public void stop(Vibrato mod) {
        _cachedTaskChain.clear();
        onStop(mod);
    }

    protected abstract void onStop(Vibrato mod);

    public abstract void onInterrupt(Vibrato mod, TaskChain other);

    protected abstract void onTick(Vibrato mod);

    public abstract float getPriority(Vibrato mod);

    public abstract boolean isActive();

    public abstract String getName();

    public List<Task> getTasks() {
        return _cachedTaskChain;
    }

    void addTaskToChain(Task task) {
        _cachedTaskChain.add(task);
    }

    public String toString() {
        return getName();
    }

}
