package dev.vibrato.tasks.movement;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.time.TimerGame;
import dev.vibrato.util.helpers.LookHelper;
import baritone.api.utils.input.Input;

/**
 * Will move around randomly while holding shift
 * Used to escape weird situations where baritone doesn't work.
 */
public class SafeRandomShimmyTask extends Task {

    private final TimerGame _lookTimer;

    public SafeRandomShimmyTask(float randomLookInterval) {
        _lookTimer  = new TimerGame(randomLookInterval);
    }
    public SafeRandomShimmyTask() {
        this(5);
    }

    @Override
    protected void onStart(Vibrato mod) {
        _lookTimer.reset();
    }

    @Override
    protected Task onTick(Vibrato mod) {

        if (_lookTimer.elapsed()) {
            Debug.logMessage("Random Orientation");
            _lookTimer.reset();
            LookHelper.randomOrientation(mod);
        }

        mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
        mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
        return null;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
        mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.SNEAK, false);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SafeRandomShimmyTask;
    }

    @Override
    protected String toDebugString() {
        return "Shimmying";
    }
}
