package dev.vibrato.tasks.construction;

/** Tracks an interaction with the target until its consumed material is resolved. */
final class PlacementAttempt {
    private static final int MAX_PENDING_TICKS = 40;
    private boolean attempted;
    private int remaining = -1;

    void interacted() { attempted = true; }

    boolean waitForResult(boolean hasMaterial) {
        if (hasMaterial) {
            remaining = -1;
            attempted = false;
            return false;
        }
        if (!attempted) return false;
        if (remaining < 0) remaining = MAX_PENDING_TICKS;
        if (remaining == 0) return false;
        --remaining;
        return true;
    }
}
