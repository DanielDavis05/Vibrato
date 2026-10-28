package dev.vibrato.tasks.movement;

/** Finite wood then durable spare-tool preparation, retained across interruptions. */
final class NetherExpeditionPlan {
    static final int LOGS = 4;
    static final int USABLE_TOOLS = 2;
    enum Stage { WOOD, TOOLS, WEAPON, COMPLETE }
    private Stage stage = Stage.WOOD;

    Stage update(int physicalLogs, int usableTools, boolean usableWeapon) {
        if (stage == Stage.WOOD && physicalLogs >= LOGS) stage = Stage.TOOLS;
        if (stage == Stage.TOOLS && usableTools >= USABLE_TOOLS) stage = Stage.WEAPON;
        if (stage == Stage.WEAPON && usableWeapon)
            stage = usableTools >= USABLE_TOOLS ? Stage.COMPLETE : Stage.TOOLS;
        return stage;
    }
    static int stoneGoal(int currentGoal, int physicalStone, int usableTools) {
        int missing = Math.max(0, USABLE_TOOLS - usableTools);
        if (missing == 0) return 0;
        // The current goal remains stable while recipe output is on the
        // cursor or in flight. Raise it only after that many actual stones
        // exist in main inventory and some are now too worn to count.
        if (currentGoal == 0 || physicalStone >= currentGoal) return physicalStone + missing;
        return currentGoal;
    }
    static int weaponGoal(int currentGoal, int physicalStoneSwords, boolean usableWeapon) {
        if (usableWeapon) return 0;
        if (currentGoal == 0 || physicalStoneSwords >= currentGoal) return physicalStoneSwords + 1;
        return currentGoal;
    }
    void reset() { stage = Stage.WOOD; }
}
