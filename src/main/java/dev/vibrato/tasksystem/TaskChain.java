package dev.vibrato.tasksystem;

import dev.vibrato.Vibrato;

import java.util.ArrayList;
import java.util.List;

public abstract class TaskChain {

    private final List<Task> _cachedTaskChain = new ArrayList<>();

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
