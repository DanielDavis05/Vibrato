package dev.vibrato.tasks.travel;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.movement.DefaultGoToDimensionTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.Dimension;
import dev.vibrato.util.helpers.WorldHelper;
import dev.vibrato.util.time.TimerGame;

/**
 * Changes dimension through the default portal routine, retrying when an attempt stalls. Each attempt has a time limit
 * and there are at most {@link #MAX_ATTEMPTS} attempts; after that the task ends without changing dimension.
 * <p>
 * The {@code portal:built} fact is symbolic: it is declared by the activity that builds or uses a portal, and this task
 * does not write facts.
 */
public class PortalTravelTask extends Task {

    private static final int MAX_ATTEMPTS = 3;
    private static final double ATTEMPT_SECONDS = 60 * 5;

    private final Dimension _target;
    private final DefaultGoToDimensionTask _goTo;
    private final TimerGame _attemptTimer = new TimerGame(ATTEMPT_SECONDS);
    private int _attempts;
    private boolean _failed;

    public PortalTravelTask(Dimension target) {
        _target = target;
        _goTo = new DefaultGoToDimensionTask(target);
    }

    @Override
    protected void onStart(Vibrato mod) {
        _attempts = 0;
        _failed = false;
        _attemptTimer.reset();
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (WorldHelper.getCurrentDimension() == _target) {
            return null;
        }
        if (_attemptTimer.elapsed()) {
            _attempts++;
            _goTo.stop(mod);
            _attemptTimer.reset();
            if (_attempts >= MAX_ATTEMPTS) {
                setDebugState("Portal travel failed after " + MAX_ATTEMPTS + " attempts");
                _failed = true;
                return null;
            }
        }
        setDebugState("Travelling to " + _target + " (attempt " + (_attempts + 1) + ")");
        return _goTo;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        _goTo.stop(mod);
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _failed || WorldHelper.getCurrentDimension() == _target;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof PortalTravelTask task && task._target == _target;
    }

    @Override
    protected String toDebugString() {
        return "Portal travel to " + _target;
    }
}
