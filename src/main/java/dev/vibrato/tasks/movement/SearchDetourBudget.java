package dev.vibrato.tasks.movement;

/** Time and travel limits for optional work during one search attempt. */
final class SearchDetourBudget {
    private static final double FIRST_SECONDS = 25;
    private static final double COOLDOWN_SECONDS = 30;
    private static final double MIN_TRAVEL_SQUARED = 12 * 12;
    private static final double SHARE = 0.15;
    private static final double MAX_DETOUR_SECONDS = 0.8;
    private double spent;
    private double started = -1;
    private double lastEnded = -1;

    void reset() { spent = 0; started = -1; lastEnded = -1; }

    boolean canStart(double elapsed, double travelSquared) {
        return started < 0 && elapsed >= FIRST_SECONDS && spent + 0.5 <= elapsed * SHARE
                && (lastEnded < 0 || (elapsed - lastEnded >= COOLDOWN_SECONDS && travelSquared >= MIN_TRAVEL_SQUARED));
    }

    void start(double elapsed) { started = elapsed; }

    boolean canContinue(double elapsed) {
        double current = elapsed - started;
        return started >= 0 && current >= 0 && current < MAX_DETOUR_SECONDS
                && spent + current < elapsed * SHARE;
    }

    void end(double elapsed) {
        if (started < 0) return;
        spent += Math.max(0, elapsed - started);
        started = -1;
        lastEnded = elapsed;
    }
}
