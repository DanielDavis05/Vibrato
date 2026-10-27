package dev.vibrato.chains;

/** Bounded food collection for autonomous play, measured in active game ticks. */
final class AutonomousFoodPolicy {
    static final int TARGET_FOOD = 12;
    private static final int SEARCH_TICKS = 20 * 60;
    private static final int BACKOFF_TICKS = 20 * 60;
    private boolean collecting;
    private int searchTicks;
    private int backoffTicks;

    void reset() { collecting = false; searchTicks = 0; backoffTicks = 0; }

    void advance(boolean collectingTick, boolean normalPlayTick) {
        if (collecting && collectingTick && ++searchTicks >= SEARCH_TICKS) {
            collecting = false;
            backoffTicks = BACKOFF_TICKS;
        } else if (!collecting && normalPlayTick && backoffTicks > 0) {
            backoffTicks--;
        }
    }

    boolean shouldCollect(int hunger, float healthFraction, int edibleFood, boolean completed) {
        if (collecting && (completed || edibleFood >= TARGET_FOOD)) {
            collecting = false;
            searchTicks = 0;
        }
        if (!collecting && backoffTicks == 0 && !completed && edibleFood == 0
                && (hunger <= 8 || (healthFraction <= 0.5f && hunger < 18))) {
            collecting = true;
            searchTicks = 0;
        }
        return collecting;
    }
}
