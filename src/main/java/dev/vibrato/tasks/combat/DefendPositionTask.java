package dev.vibrato.tasks.combat;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.movement.GetToBlockTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.time.TimerGame;
import net.minecraft.util.math.BlockPos;

/** Holds a spot for a number of seconds: fights off hostiles that come close, and walks back to the spot after. */
public class DefendPositionTask extends Task {

    private static final double GUARD_RANGE = 12;
    private static final double POST_RADIUS = 3;

    private final int _seconds;
    private final TimerGame _timer;
    private final ClearNearbyHostilesTask _clear = new ClearNearbyHostilesTask(GUARD_RANGE);
    private BlockPos _post;

    public DefendPositionTask(int seconds) {
        _seconds = seconds;
        _timer = new TimerGame(seconds);
    }

    @Override
    protected void onStart(Vibrato mod) {
        _post = mod.getPlayer().getBlockPos();
        _timer.reset();
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (!_clear.isFinished(mod)) {
            return _clear;
        }
        if (!_post.isWithinDistance(mod.getPlayer().getPos(), POST_RADIUS)) {
            setDebugState("Returning to post");
            return new GetToBlockTask(_post);
        }
        setDebugState("Holding position");
        return null;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _timer.elapsed();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof DefendPositionTask task && task._seconds == _seconds;
    }

    @Override
    protected String toDebugString() {
        return "Defend position for " + _seconds + " seconds";
    }
}
