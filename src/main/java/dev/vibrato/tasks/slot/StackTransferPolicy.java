package dev.vibrato.tasks.slot;

/** Choose a smaller cursor stack when it can still supply the requested transfer. */
final class StackTransferPolicy {
    private StackTransferPolicy() {}

    static int pickupButton(int available, int needed) {
        int half = (available + 1) / 2;
        return needed > 0 && half >= needed && half < available ? 1 : 0;
    }
}
