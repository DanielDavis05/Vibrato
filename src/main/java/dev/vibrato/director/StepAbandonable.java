package dev.vibrato.director;

/**
 * A user task that runs as a sequence of steps. Navigation safety abandons the current step through this, instead of
 * cancelling the whole task.
 */
public interface StepAbandonable {

    /** Fails the step that is running now, if any, and moves on to the next one. */
    void abandonCurrentStep(String reason);

    /** Changes every time a new step starts, so per-step clocks can restart. */
    int stepSerial();
}
