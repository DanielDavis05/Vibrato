package dev.vibrato.human;

import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Occasional glances while a bot waits, so an idle bot looks around instead of standing like a statue.
 */
public final class IdleBehavior {

    private static final int MIN_INTERVAL_TICKS = 80;
    private static final int MAX_INTERVAL_TICKS = 240;
    private static final float MAX_GLANCE_DEG = 25;
    private static final int MIN_GLANCE_TICKS = 10;
    private static final int MAX_GLANCE_TICKS = 30;

    /**
     * A look away from the current direction, held for {@code durationTicks}.
     */
    public record Glance(float yawDeg, float pitchDeg, int durationTicks) {}

    private long nextGlanceTick = -1;

    /**
     * Call once per tick. While idle, a glance starts every 80 to 240 ticks, and the call that starts it returns it.
     * Busy ticks return nothing and restart the schedule.
     */
    public Optional<Glance> poll(long tick, boolean idle, RandomGenerator rng) {
        if (!idle || !HumanComponents.behavior()) {
            nextGlanceTick = -1;
            return Optional.empty();
        }
        if (nextGlanceTick < 0) {
            nextGlanceTick = tick + interval(rng);
            return Optional.empty();
        }
        if (tick < nextGlanceTick) {
            return Optional.empty();
        }
        nextGlanceTick = tick + interval(rng);
        float yaw = (rng.nextFloat() * 2 - 1) * MAX_GLANCE_DEG;
        float pitch = (rng.nextFloat() * 2 - 1) * MAX_GLANCE_DEG;
        int duration = MIN_GLANCE_TICKS + rng.nextInt(MAX_GLANCE_TICKS - MIN_GLANCE_TICKS + 1);
        return Optional.of(new Glance(yaw, pitch, duration));
    }

    private static int interval(RandomGenerator rng) {
        return MIN_INTERVAL_TICKS + rng.nextInt(MAX_INTERVAL_TICKS - MIN_INTERVAL_TICKS + 1);
    }
}
