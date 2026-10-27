package dev.vibrato.util.progresscheck;

import dev.vibrato.util.time.TimerGame;
import net.minecraft.util.math.Vec3d;

import java.util.function.BooleanSupplier;

/** Measures displacement in each time window, including movement back along an earlier route. */
final class MovementWindowProgressChecker implements IProgressChecker<Vec3d> {
    private final double _minimumDistance;
    private final BooleanSupplier _elapsed;
    private final Runnable _resetTimer;
    private Vec3d _start;
    private boolean _failed;

    MovementWindowProgressChecker(double timeout, double minimumDistance) {
        this(new TimerGame(timeout), minimumDistance);
    }

    private MovementWindowProgressChecker(TimerGame timer, double minimumDistance) {
        this(minimumDistance, timer::elapsed, timer::reset);
    }

    MovementWindowProgressChecker(double minimumDistance, BooleanSupplier elapsed, Runnable resetTimer) {
        _minimumDistance = minimumDistance;
        _elapsed = elapsed;
        _resetTimer = resetTimer;
        reset();
    }

    @Override
    public void setProgress(Vec3d position) {
        if (_start == null) {
            _start = position;
            _resetTimer.run();
        } else if (_elapsed.getAsBoolean()) {
            if (position.distanceTo(_start) < _minimumDistance) {
                _failed = true;
            }
            _start = position;
            _resetTimer.run();
        }
    }

    @Override
    public boolean failed() {
        return _failed;
    }

    @Override
    public void reset() {
        _start = null;
        _failed = false;
        _resetTimer.run();
    }
}
