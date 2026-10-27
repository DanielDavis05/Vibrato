package dev.vibrato.nav;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;

import java.util.List;

/** Bounds scoped retries across changes to a manual root's dependency tasks. */
final class ResourceRecovery {
    private static final int MAX_RECOVERIES = 2;
    private Task root;
    private int recoveries;

    /** Called before interrupting the user chain, with its most recent task snapshot. */
    boolean attempt(Vibrato mod, Task currentRoot, List<Task> tasks) {
        if (root != currentRoot) {
            root = currentRoot;
            recoveries = 0;
        }
        if (currentRoot == null || recoveries >= MAX_RECOVERIES) return false;
        for (int i = tasks.size() - 1; i >= 0; i--) {
            Task task = tasks.get(i);
            if (task.isActive() && task.recoverFromStall(mod)) {
                recoveries++;
                return true;
            }
        }
        return false;
    }

    /** Reset only for genuine resource progress or a new root, not a recovery action. */
    void reset() {
        root = null;
        recoveries = 0;
    }
}
