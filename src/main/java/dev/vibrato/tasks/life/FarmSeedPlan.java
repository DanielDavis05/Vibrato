package dev.vibrato.tasks.life;

/** A bounded field-sized wheat seed acquisition that survives partial pickups. */
final class FarmSeedPlan {
    private int committedGoal;
    private boolean stocked;

    boolean gathering(int held) { return committedGoal > held; }

    int seedGoal(int fieldSize, int planted, int held, boolean ripe) {
        // Mature harvests are real progress and take priority over seed gathering.
        if (ripe) return 0;
        // Finish planting a collected batch before reconsidering its budget;
        // world updates can lag an individual seed decrement by a tick.
        if (stocked && held > 0) return 0;
        if (stocked) stocked = false;
        if (committedGoal > 0) {
            if (held < committedGoal) return committedGoal;
            committedGoal = 0;
            stocked = true;
            return 0;
        }
        int missing = Math.max(0, fieldSize - planted);
        if (held < missing) committedGoal = missing;
        else if (missing > 0) stocked = true;
        return committedGoal;
    }
}
