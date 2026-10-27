package dev.vibrato.social;

import baritone.api.utils.input.Input;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.movement.GetToEntityTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.LookHelper;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Walks up to nearby players or bots, looks at them, waves by sneaking twice, and says hello. Each person is greeted
 * once, and the whole task has a time cap.
 */
public class GreetNearbyPlayersTask extends Task {

    private static final double SEARCH_RANGE = 32;
    private static final double GREET_RANGE = 5;
    private static final int WAVE_START_TICK = 10;
    private static final int WAVE_SECOND_TAP_TICK = 16;
    private static final int GREET_AFTER_TICK = 25;
    private static final int MAX_TICKS = 2400;

    private final int _maxGreetings;
    private final Set<String> _greeted = new HashSet<>();
    private final MessageBank _bank = new MessageBank();

    private boolean _done;
    private int _ticks;
    private int _greetings;
    private int _gazeTicks;
    private PlayerEntity _target;
    private ChatTask _chat;

    public GreetNearbyPlayersTask(int maxGreetings) {
        if (maxGreetings < 1) {
            throw new IllegalArgumentException("maxGreetings must be at least 1");
        }
        _maxGreetings = maxGreetings;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _greeted.clear();
        _done = false;
        _ticks = 0;
        _greetings = 0;
        _gazeTicks = 0;
        _target = null;
        _chat = null;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_done) {
            return null;
        }
        if (++_ticks > MAX_TICKS) {
            _done = true;
            return null;
        }
        if (_chat != null) {
            if (!_chat.isFinished(mod)) {
                return _chat;
            }
            _chat = null;
            _greetings++;
            _target = null;
            _gazeTicks = 0;
            if (_greetings >= _maxGreetings) {
                _done = true;
                return null;
            }
        }
        if (_target == null) {
            _target = SocialTargets.nearestOtherPlayer(mod, SEARCH_RANGE, _greeted).orElse(null);
            if (_target == null) {
                setDebugState("Nobody new nearby");
                return null;
            }
        }
        if (!_target.isAlive()) {
            _target = null;
            return null;
        }
        if (_target.distanceTo(mod.getPlayer()) > GREET_RANGE) {
            setDebugState("Walking to " + _target.getName().getString());
            return new GetToEntityTask(_target, GREET_RANGE - 1);
        }
        setDebugState("Greeting " + _target.getName().getString());
        LookHelper.lookAt(mod, _target.getEyePos());
        ++_gazeTicks;
        if (_gazeTicks == WAVE_START_TICK || _gazeTicks == WAVE_SECOND_TAP_TICK) {
            mod.getInputControls().tryPress(Input.SNEAK);
        }
        if (_gazeTicks >= GREET_AFTER_TICK) {
            String name = _target.getName().getString();
            _greeted.add(name);
            _chat = new ChatTask(_bank.render("greeting", new java.util.Random(), Map.of("player", name)));
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
        return other instanceof GreetNearbyPlayersTask task && task._maxGreetings == _maxGreetings;
    }

    @Override
    protected String toDebugString() {
        return "Greeting up to " + _maxGreetings + " nearby players";
    }
}
