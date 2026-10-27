package dev.vibrato.tasks.resources;

/** Decides whether nearby drops can complete all requested inventory targets. */
final class CompletionDropPolicy {
    private CompletionDropPolicy() {}

    static boolean canFinish(int[] held, int[] required, int[] nearby) {
        boolean needsItems = false;
        for (int i = 0; i < required.length; i++) {
            if (held[i] < required[i]) {
                needsItems = true;
                if ((long) held[i] + nearby[i] < required[i]) return false;
            }
        }
        return needsItems;
    }
}
