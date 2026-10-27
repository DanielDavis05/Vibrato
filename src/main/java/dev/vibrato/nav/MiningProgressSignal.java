package dev.vibrato.nav;

/** Recognizes actual changes in event-based mining state without rewarding a stale event indefinitely. */
final class MiningProgressSignal {
    private Long _trackedPosition;
    private double _lastProgress;

    Long trackedPosition() {
        return _trackedPosition;
    }

    /** Positions are packed immutable block coordinates. Air marks a completed tracked block. */
    boolean observe(Long currentPosition, double progress, boolean currentIsAir, boolean trackedIsAir) {
        boolean completed = _trackedPosition != null && trackedIsAir;
        if (completed) _trackedPosition = null;
        if (currentPosition == null || currentIsAir) return completed;
        if (!currentPosition.equals(_trackedPosition)) {
            _trackedPosition = currentPosition;
            _lastProgress = progress;
            return true;
        }
        boolean advanced = progress > _lastProgress;
        _lastProgress = progress;
        return completed || advanced;
    }

    void reset() {
        _trackedPosition = null;
        _lastProgress = 0;
    }
}
