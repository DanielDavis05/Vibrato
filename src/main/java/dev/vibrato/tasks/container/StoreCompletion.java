package dev.vibrato.tasks.container;

import java.util.function.BooleanSupplier;

/** Acquisition-and-deposit goals finish on confirmed stored quantities, never on an empty inventory. */
final class StoreCompletion {
    private StoreCompletion() {}

    static boolean finished(boolean collectMissing, int[] requested, int[] deposited, BooleanSupplier nothingAvailable) {
        if (requested.length != deposited.length) throw new IllegalArgumentException("Mismatched storage target counts");
        if (!collectMissing) return nothingAvailable.getAsBoolean();
        for (int i = 0; i < requested.length; i++) if (deposited[i] < requested[i]) return false;
        return true;
    }
}
