package dev.vibrato.social;

import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.LookHelper;

/** A short, bounded body gesture: two jumps, a burst of sneak taps, or a slow spin on the spot. */
public class EmoteTask extends Task {

    public enum Emote {
        JUMP(20),
        SNEAK_SPAM(40),
        SPIN(60);

        private final int _durationTicks;

        Emote(int durationTicks) {
            _durationTicks = durationTicks;
        }

        public int durationTicks() {
            return _durationTicks;
        }
    }

    private static final int SPIN_DEGREES_PER_TICK = 6;

    private final Emote _emote;
    private int _ticks;
    private float _startYaw;
    private float _pitch;

    public EmoteTask(Emote emote) {
        _emote = emote;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _ticks = 0;
        _startYaw = mod.getPlayer().getYaw();
        _pitch = mod.getPlayer().getPitch();
    }

    @Override
    protected Task onTick(Vibrato mod) {
        switch (_emote) {
            case JUMP -> {
                if (_ticks == 0 || _ticks == 10) {
                    mod.getInputControls().tryPress(Input.JUMP);
                }
            }
            case SNEAK_SPAM -> {
                if (_ticks % 4 == 0) {
                    mod.getInputControls().tryPress(Input.SNEAK);
                }
            }
            case SPIN -> LookHelper.lookAt(mod, new Rotation(_startYaw + _ticks * SPIN_DEGREES_PER_TICK, _pitch));
        }
        ++_ticks;
        return null;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _ticks >= _emote.durationTicks();
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof EmoteTask task && task._emote == _emote;
    }

    @Override
    protected String toDebugString() {
        return "Emote " + _emote.name().toLowerCase();
    }
}
