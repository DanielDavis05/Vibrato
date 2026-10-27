package dev.vibrato.tasks.speedrun;

/** Limits retries of the placement handoff failure seen in long resource stages. */
final class GamerStageRecovery {
    enum Stage { FOOD }
    private static final int MAX_RETRIES = 2;
    private final int[] retries = new int[Stage.values().length];

    boolean retry(Stage stage, String reason) {
        // Only the observed missing placement material can plausibly be transient.
        // Protected materials, full inventory and other failures still propagate.
        if (!isPlacementHandoff(reason) || retries[stage.ordinal()] >= MAX_RETRIES) return false;
        retries[stage.ordinal()]++;
        return true;
    }

    void completed(Stage stage) { retries[stage.ordinal()] = 0; }

    static boolean isPlacementHandoff(String reason) {
        return reason != null && reason.startsWith("PlaceBlockTask: No usable block to place at ");
    }
}
