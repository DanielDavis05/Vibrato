package dev.vibrato.tasks.movement;

/** Whether a requested placement item is actually usable from the selected main hand. */
public final class ClutchItemPolicy {
    private ClutchItemPolicy() { }

    public static boolean mayUse(boolean waterBucket, boolean ultrawarm, boolean selectedMainHandMatches) {
        return selectedMainHandMatches && (!waterBucket || !ultrawarm);
    }
}
