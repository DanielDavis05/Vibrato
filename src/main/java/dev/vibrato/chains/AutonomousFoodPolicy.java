package dev.vibrato.chains;

import dev.vibrato.director.DirectorTask;
import dev.vibrato.tasks.ResourceTask;
import dev.vibrato.tasksystem.IAllowsAutonomousFood;
import dev.vibrato.tasksystem.Task;

/** Bounded food collection for autonomous play, measured in active game ticks. */
final class AutonomousFoodPolicy {
    static final int TARGET_FOOD = 12;
    static boolean allowsRoot(Class<? extends Task> root) {
        return root != null && (DirectorTask.class.isAssignableFrom(root) || ResourceTask.class.isAssignableFrom(root)
                || IAllowsAutonomousFood.class.isAssignableFrom(root));
    }
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
        boolean recovered = hunger >= 18 && healthFraction >= 0.75f;
        if (collecting && (completed || edibleFood >= TARGET_FOOD || recovered)) {
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
