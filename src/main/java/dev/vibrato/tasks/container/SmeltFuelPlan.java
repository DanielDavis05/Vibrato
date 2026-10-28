package dev.vibrato.tasks.container;

import dev.vibrato.tasks.resources.CollectFuelTask;

/** Keeps a requested coal acquisition stable while other recipes consume wood fuel. */
final class SmeltFuelPlan {
    private double committedSmelts;

    double committedSmelts(double neededSmelts, int coalInMainInventory) {
        if (neededSmelts <= 0 || coalInMainInventory >= CollectFuelTask.requiredCoal(committedSmelts)) {
            committedSmelts = 0;
        }
        return committedSmelts;
    }

    double collectSmelts(double neededSmelts, double inventoryFuel, int coalInMainInventory) {
        if (neededSmelts <= 0) { committedSmelts = 0; return 0; }
        if (committedSmelts(neededSmelts, coalInMainInventory) > 0) return committedSmelts;
        if (inventoryFuel < neededSmelts) committedSmelts = neededSmelts;
        return committedSmelts;
    }
}
