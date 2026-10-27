package dev.vibrato.social;

import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.entity.GiveItemToPlayerTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;

import java.util.Optional;

/** Gives a catalogued item to the nearest other player or bot, using {@link GiveItemToPlayerTask}. */
public class ShareItemsTask extends Task {

    private static final double SEARCH_RANGE = 16;

    private final String _itemName;
    private final int _count;

    private GiveItemToPlayerTask _give;
    private boolean _done;

    public ShareItemsTask(String itemName, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1");
        }
        _itemName = itemName;
        _count = count;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _give = null;
        _done = false;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_give == null) {
            Optional<String> recipient = SocialTargets.nearestOtherPlayerName(mod, SEARCH_RANGE);
            if (recipient.isEmpty()) {
                Debug.logWarning("No player or bot nearby to share " + _count + " " + _itemName + " with");
                _done = true;
                return null;
            }
            _give = new GiveItemToPlayerTask(recipient.get(), new ItemTarget(_itemName, _count));
        }
        return _give;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _done || (_give != null && (_give.stopped() || _give.isFinished(mod)));
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ShareItemsTask task && task._itemName.equals(_itemName) && task._count == _count;
    }

    @Override
    protected String toDebugString() {
        return "Sharing " + _count + " " + _itemName;
    }
}
