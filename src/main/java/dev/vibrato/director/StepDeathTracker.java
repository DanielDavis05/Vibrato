package dev.vibrato.director;

import java.util.function.Predicate;

/** Retains the last player object across chain interruptions and detects an actual death before a respawn. */
final class StepDeathTracker<T> {
    enum State { ALIVE, DEAD, RESPAWNED }

    private T previous;
    private boolean previousDied;

    State observe(T current, Predicate<T> dead) {
        if (current == null) {
            if (previous != null && (previousDied || dead.test(previous))) {
                previousDied = true;
                return State.DEAD;
            }
            return State.ALIVE;
        }
        if (previous != null && previous != current) {
            if (previousDied || dead.test(previous)) {
                previous = current;
                previousDied = false;
                return State.RESPAWNED;
            }
            previousDied = false;
        }
        previous = current;
        if (current != null && dead.test(current)) {
            previousDied = true;
            return State.DEAD;
        }
        return State.ALIVE;
    }
}
