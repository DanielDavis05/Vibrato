package dev.vibrato.human;

import baritone.api.utils.Rotation;
import dev.vibrato.Debug;

/**
 * Opt-in trace for finding what pulls the camera away from the pitch a caller asked for. Enabled with
 * {@code -Dvibrato.debugLook=true}. Logs, while the camera stays more than 20 degrees from the requested pitch for more
 * than 5 ticks in a row, the requested and previous pitch, the smoothed output and the code path that asked.
 */
public final class LookTrace {

    static final boolean ENABLED = Boolean.getBoolean("vibrato.debugLook");

    private static final double GAP_DEG = 20;
    private static final int STREAK_TICKS = 5;
    private static final int LOG_EVERY_TICKS = 10;
    public static final String BARITONE = "baritone";

    private static String source = BARITONE;
    private static Rotation previous;
    private static Rotation requested;
    private static Rotation smoothed;
    private static String requestSource;
    private static int streak;
    private static int sinceLog;

    private LookTrace() {}

    /**
     * Names the code path about to ask for a look, so the trace can say which writer moved the camera. Reset with
     * {@link #BARITONE} afterwards.
     */
    public static void source(String name) {
        source = name;
    }

    static void observe(Rotation previous, Rotation requested, Rotation smoothed) {
        if (!ENABLED) {
            return;
        }
        LookTrace.previous = previous;
        LookTrace.requested = requested;
        LookTrace.smoothed = smoothed;
        LookTrace.requestSource = source;
    }

    /**
     * Called once per tick with the last observed request. Keeps the streak and logs it, at most every few ticks.
     */
    static void endTick() {
        if (!ENABLED || requested == null) {
            return;
        }
        double gap = Math.abs(requested.getPitch() - previous.getPitch());
        streak = gap > GAP_DEG ? streak + 1 : 0;
        sinceLog++;
        if (streak > STREAK_TICKS && sinceLog >= LOG_EVERY_TICKS) {
            sinceLog = 0;
            Debug.logMessage(String.format(
                    "look trace [%s]: requested pitch %.1f, previous %.1f, smoothed %.1f, off by %.1f for %d ticks",
                    requestSource, requested.getPitch(), previous.getPitch(), smoothed.getPitch(), gap, streak));
        }
        requested = null;
    }
}
