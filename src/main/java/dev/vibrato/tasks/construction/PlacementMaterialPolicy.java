package dev.vibrato.tasks.construction;

/** Decides how to handle a material while a container handoff is settling. */
final class PlacementMaterialPolicy {
    enum Action { READY, MOVE_CURSOR, WAIT, FAIL }

    static Action decide(boolean inMainInventory, boolean matchingCursor, int handoffTicksRemaining) {
        if (inMainInventory) return Action.READY;
        if (matchingCursor) return Action.MOVE_CURSOR;
        return handoffTicksRemaining > 0 ? Action.WAIT : Action.FAIL;
    }
}
