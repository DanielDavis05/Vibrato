package dev.vibrato.tasks.container;

/** Retains a selected container prerequisite until it is available or replaced. */
final class ContainerAcquisition {
    private boolean selected;

    void select() { selected = true; }
    void completedOrReplaced() { selected = false; }
    boolean needsCompletion(boolean containerItemAvailable) {
        if (containerItemAvailable) selected = false;
        return selected;
    }
}
