package dev.vibrato.human.record;

/**
 * One line of a motion trace. The JSONL layout is described in {@code docs/HUMAN_MOTION_PLAN.md}.
 */
public sealed interface TraceEvent {

    /**
     * First line of a trace. {@code source} is {@code "human"} or {@code "bot"}.
     */
    record Header(int version, String source, String mcVersion, int guiScale, double sensitivity, int fps, String note)
            implements TraceEvent {}

    /**
     * Raw mouse delta in pixels for one frame. {@code ms} is monotonic milliseconds.
     */
    record Mouse(long ms, double dx, double dy) implements TraceEvent {}

    /**
     * Key transition. {@code key} is one of forward, back, left, right, jump, sprint, sneak, attack, use.
     */
    record Key(long ms, String key, boolean down) implements TraceEvent {}

    /**
     * Player state sampled once per client tick. The {@code next*} fields are the next path node while pathing and
     * {@code null} otherwise.
     */
    record Tick(
            long n, long ms,
            double x, double y, double z,
            double vx, double vy, double vz,
            double yaw, double pitch,
            boolean ground, boolean sprint,
            Double nextX, Double nextY, Double nextZ
    ) implements TraceEvent {}
}
