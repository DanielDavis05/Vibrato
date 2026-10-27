package dev.vibrato.social;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.movement.FollowPlayerTask;
import dev.vibrato.tasksystem.Task;

import java.util.Optional;

/** Follows the nearest other player or bot for a fixed number of seconds, then stops. */
public class FollowFriendTask extends Task {

    private static final double SEARCH_RANGE = 32;

    private final int _seconds;

    private String _friend;
    private int _ticks;
    private boolean _done;

    public FollowFriendTask(int seconds) {
        if (seconds < 1) {
            throw new IllegalArgumentException("seconds must be at least 1");
        }
        _seconds = seconds;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _friend = null;
        _ticks = 0;
        _done = false;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_done) {
            return null;
        }
        if (_friend == null) {
            Optional<String> nearest = SocialTargets.nearestOtherPlayerName(mod, SEARCH_RANGE);
            if (nearest.isEmpty()) {
                setDebugState("No friend nearby");
                _done = true;
                return null;
            }
            _friend = nearest.get();
        }
        if (++_ticks >= _seconds * 20) {
            _done = true;
            return null;
        }
        setDebugState("Following " + _friend);
        return new FollowPlayerTask(_friend);
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _done;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof FollowFriendTask task && task._seconds == _seconds;
    }

    @Override
    protected String toDebugString() {
        return "Following a friend for " + _seconds + "s";
    }
}
