package dev.vibrato.social;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.movement.PickupDroppedItemTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;

import java.util.Map;
import java.util.Random;

/**
 * Asks in chat for an item, then waits up to a minute and picks up any matching drop that appears nearby.
 */
public class RequestHelpTask extends Task {

    private static final int WAIT_TICKS = 1200;

    private final String _itemName;
    private final MessageBank _bank = new MessageBank();

    private ChatTask _chat;
    private PickupDroppedItemTask _pickup;
    private boolean _done;
    private int _ticks;

    public RequestHelpTask(String itemName) {
        if (!TaskCatalogue.taskExists(itemName)) {
            throw new IllegalArgumentException("Not a catalogued item: " + itemName);
        }
        _itemName = itemName;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _done = false;
        _ticks = 0;
        _pickup = null;
        String line = _bank.render("asking_for_help", new Random(),
                Map.of("item", _itemName.replace('_', ' '), "count", "1", "player", "anyone"));
        _chat = new ChatTask(line);
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_done) {
            return null;
        }
        if (_chat != null) {
            if (!_chat.isFinished(mod)) {
                return _chat;
            }
            _chat = null;
        }
        if (_pickup != null) {
            if (_pickup.stopped() || _pickup.isFinished(mod)) {
                _done = true;
                return null;
            }
            return _pickup;
        }
        if (++_ticks > WAIT_TICKS) {
            _done = true;
            return null;
        }
        setDebugState("Waiting for " + _itemName);
        if (mod.getEntityTracker().getClosestItemDrop(TaskCatalogue.getItemMatches(_itemName)).isPresent()) {
            _pickup = new PickupDroppedItemTask(new ItemTarget(_itemName, 1), false);
            return _pickup;
        }
        return null;
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
        return other instanceof RequestHelpTask task && task._itemName.equals(_itemName);
    }

    @Override
    protected String toDebugString() {
        return "Asking for help with " + _itemName;
    }
}
