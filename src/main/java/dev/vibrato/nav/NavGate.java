package dev.vibrato.nav;

/**
 * Decides whether stuck detection and its actions must stay quiet. Other chains and deliberate stillness own the bot's
 * movement at those times, so an unstuck action would only fight them.
 */
public final class NavGate {

    private NavGate() {}

    /** The inputs are what the navigation chain can observe about the rest of the bot this tick. */
    public static boolean suppressed(boolean mobDefenseActive, boolean foodActive, boolean mlgActive,
                                     boolean deathMenuOpen, boolean containerOpen, boolean standingStill) {
        return mobDefenseActive || foodActive || mlgActive || deathMenuOpen || containerOpen || standingStill;
    }
}
