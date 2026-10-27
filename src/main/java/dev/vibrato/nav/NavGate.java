package dev.vibrato.nav;

/**
 * Decides whether stuck detection and its actions must stay quiet. Other chains and deliberate stillness own the bot's
 * movement at those times, so an unstuck action would only fight them.
 */
public final class NavGate {

    private NavGate() {}

    /** Ticks after the last block-break tick during which the bot still counts as mining. */
    public static final long BREAK_GRACE_TICKS = 60;
    /** Marks "never". */
    public static final long NEVER = Long.MIN_VALUE;

    /** True within {@link #BREAK_GRACE_TICKS} of the last tick that was spent breaking a block. */
    public static boolean withinBreakGrace(long tick, long lastBreakTick) {
        return lastBreakTick != NEVER && tick - lastBreakTick <= BREAK_GRACE_TICKS;
    }

    /** Planning ahead is not a pause when a current path is still executing. */
    public static boolean waitingForPath(boolean pathing, boolean calculationInProgress) {
        return !pathing && calculationInProgress;
    }

    /** The inputs are what the navigation chain can observe about the rest of the bot this tick. */
    public static boolean suppressed(boolean mobDefenseActive, boolean foodActive, boolean mlgActive,
                                     boolean deathMenuOpen, boolean containerOpen, boolean standingStill) {
        return mobDefenseActive || foodActive || mlgActive || deathMenuOpen || containerOpen || standingStill;
    }
}
