package dev.vibrato.util.progresscheck;

import dev.vibrato.util.time.TimerGame;
import net.minecraft.util.math.Vec3d;

import java.util.function.BooleanSupplier;

/** Measures displacement in each time window, including movement back along an earlier route. */
final class MovementWindowProgressChecker implements IProgressChecker<Vec3d> {
    private final double _minimumDistance;
    private final int _allowedAttempts;
    private final BooleanSupplier _elapsed;
    private final Runnable _resetTimer;
    private Vec3d _start;
    private int _failedWindows;

    MovementWindowProgressChecker(double timeout, double minimumDistance, int allowedAttempts) {
        this(new TimerGame(timeout), minimumDistance, allowedAttempts);
    }

    private MovementWindowProgressChecker(TimerGame timer, double minimumDistance, int allowedAttempts) {
        this(minimumDistance, allowedAttempts, timer::elapsed, timer::reset);
    }

    MovementWindowProgressChecker(double minimumDistance, int allowedAttempts, BooleanSupplier elapsed, Runnable resetTimer) {
        if (allowedAttempts < 1) throw new IllegalArgumentException("allowedAttempts must be positive");
        _minimumDistance = minimumDistance;
        _allowedAttempts = allowedAttempts;
        _elapsed = elapsed;
        _resetTimer = resetTimer;
        reset();
    }

    @Override
    public void setProgress(Vec3d position) {
        if (failed()) return;
        if (_start == null) {
            _start = position;
            _resetTimer.run();
        } else if (_elapsed.getAsBoolean()) {
            if (position.distanceTo(_start) < _minimumDistance) {
                _failedWindows++;
            } else {
                _failedWindows = 0;
            }
            _start = position;
            _resetTimer.run();
        }
    }

    @Override
    public boolean failed() {
        return _failedWindows >= _allowedAttempts;
    }

    @Override
    public void reset() {
        _start = null;
        _failedWindows = 0;
        _resetTimer.run();
    }
}
