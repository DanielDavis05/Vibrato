package dev.vibrato.eventbus.events;

import dev.vibrato.tasksystem.Task;

public class TaskFinishedEvent {
    public double durationSeconds;
    public Task lastTaskRan;
    public final boolean cancelled;

    public TaskFinishedEvent(double durationSeconds, Task lastTaskRan) {
        this(durationSeconds, lastTaskRan, false);
    }

    public TaskFinishedEvent(double durationSeconds, Task lastTaskRan, boolean cancelled) {
        this.durationSeconds = durationSeconds;
        this.lastTaskRan = lastTaskRan;
        this.cancelled = cancelled;
    }
}
