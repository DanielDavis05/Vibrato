package dev.vibrato.tasksystem;

import dev.vibrato.Vibrato;

/**
 * Lets a task declare that it's parent can NOT interrupt itself, and that this task MUST keep executing.
 */
public interface ITaskCanForce {

    /**
     * @param interruptingCandidate This task will try to interrupt our current task.
     * @return Whether the task should forcefully keep going, even when the parent decides it shouldn't
     */
    boolean shouldForce(Vibrato mod, Task interruptingCandidate);
}
