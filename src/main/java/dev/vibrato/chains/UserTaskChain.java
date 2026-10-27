package dev.vibrato.chains;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;
import dev.vibrato.eventbus.EventBus;
import dev.vibrato.eventbus.events.TaskFinishedEvent;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.tasksystem.TaskRunner;
import dev.vibrato.util.time.Stopwatch;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

// A task chain that runs a user defined task at the same priority.
// This basically replaces our old Task Runner.
@SuppressWarnings("ALL")
public class UserTaskChain extends SingleTaskChain {

    private final Stopwatch _taskStopwatch = new Stopwatch();
    private Runnable _currentOnFinish = null;

    private boolean _runningIdleTask;
    private boolean _nextTaskIdleFlag;

    public UserTaskChain(TaskRunner runner) {
        super(runner);
    }

    private static String prettyPrintTimeDuration(double seconds) {
        int minutes = (int) (seconds / 60);
        int hours = minutes / 60;
        int days = hours / 24;

        String result = "";
        if (days != 0) {
            result += days + " days ";
        }
        if (hours != 0) {
            result += (hours % 24) + " hours ";
        }
        if (minutes != 0) {
            result += (minutes % 60) + " minutes ";
        }
        if (!result.equals("")) {
            result += "and ";
        }
        result += String.format("%.3f", (seconds % 60));
        return result;
    }

    @Override
    protected void onTick(Vibrato mod) {

        // Pause if we're not loaded into a world.
        if (!mod.inGame()) return;

        super.onTick(mod);
        if (_mainTask != null && _mainTask.failureReason() != null && _mainTask.canBeInterruptedBy(mod, null)) {
            Debug.logMessage("Task failed: " + _mainTask.failureReason());
            cancel(mod);
        }
    }

    @Override
    protected boolean churnGuardEnabled() { return true; }

    @Override
    protected boolean churnGuardSuppressed(Vibrato mod) {
        if (MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?>) {
            resetChurnGuard();
            return true;
        }
        return false;
    }

    public void cancel(Vibrato mod) {
        // Cancellation is not successful completion: discard command-chain continuations
        // before stopping, including when the task has been scheduled but not ticked yet.
        _currentOnFinish = null;
        if (_mainTask != null) {
            Task cancelledTask = _mainTask;
            stop(mod);
            mod.getTaskRunner().disable();
            mod.getClientBaritone().getInputOverrideHandler().clearAllKeys();
            if (!_runningIdleTask) {
                double seconds = _taskStopwatch.time();
                Debug.logInternal("User task cancelled after %s.", prettyPrintTimeDuration(seconds));
                EventBus.publish(new TaskFinishedEvent(seconds, cancelledTask, true));
            }
            _runningIdleTask = false;
            _nextTaskIdleFlag = false;
        }
    }

    @Override
    public float getPriority(Vibrato mod) {
        return 50;
    }

    @Override
    public String getName() {
        return "User Tasks";
    }

    public void runTask(Vibrato mod, Task task, Runnable onFinish) {
        _runningIdleTask = _nextTaskIdleFlag;
        _nextTaskIdleFlag = false;

        _currentOnFinish = onFinish;

        if (!_runningIdleTask) {
            Debug.logMessage("User Task Set: " + task.toString());
        }
        mod.getTaskRunner().enable();
        _taskStopwatch.begin();
        setTask(task);

        if (mod.getModSettings().failedToLoad()) {
            Debug.logWarning("Settings file failed to load at some point. Check logs for more info, or delete the" +
                    " file to re-load working settings.");
        }
    }

    @Override
    protected void onTaskFinish(Vibrato mod) {
        Task oldTask = _mainTask;
        boolean shouldIdle = mod.getModSettings().shouldRunIdleCommandWhenNotActive();
        if (!shouldIdle) {
            // Stop.
            mod.getTaskRunner().disable();
            // Extra reset. Sometimes baritone is laggy and doesn't properly reset our press
            mod.getClientBaritone().getInputOverrideHandler().clearAllKeys();
        }
        double seconds = _taskStopwatch.time();
        _mainTask = null;
        Runnable onFinish = _currentOnFinish;
        _currentOnFinish = null;
        if (onFinish != null) {
            onFinish.run();
        }
        // our `onFinish` might have triggered more tasks.
        boolean actuallyDone = _mainTask == null;
        if (actuallyDone) {
            if (!_runningIdleTask) {
                Debug.logMessage("User task FINISHED. Took %s seconds.", prettyPrintTimeDuration(seconds));
                EventBus.publish(new TaskFinishedEvent(seconds, oldTask));
            }
            if (shouldIdle) {
                Vibrato.getCommandExecutor().executeWithPrefix(mod.getModSettings().getIdleCommand());
                signalNextTaskToBeIdleTask();
                _runningIdleTask = true;
            }
        }
    }

    public boolean isRunningIdleTask() {
        return isActive() && _runningIdleTask;
    }

    // The next task will be an idle task.
    public void signalNextTaskToBeIdleTask() {
        _nextTaskIdleFlag = true;
    }
}
