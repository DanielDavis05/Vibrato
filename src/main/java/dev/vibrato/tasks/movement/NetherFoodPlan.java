package dev.vibrato.tasks.movement;

/** Stock food once before portal construction when the journey starts empty-handed. */
final class NetherFoodPlan {
    static final int FOOD_UNITS = 16;
    private boolean collecting;
    private boolean prepared;

    boolean shouldCollect(boolean buildingPortal, int foodUnits) {
        if (!buildingPortal || prepared) return false;
        if (!collecting) {
            if (foodUnits > 0) { prepared = true; return false; }
            collecting = true;
        }
        if (foodUnits >= FOOD_UNITS) { prepared = true; collecting = false; return false; }
        return true;
    }

    void reset() { collecting = false; prepared = false; }
}
