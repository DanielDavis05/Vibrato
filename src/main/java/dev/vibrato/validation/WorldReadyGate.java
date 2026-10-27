package dev.vibrato.validation;

/** Wait for the first unobstructed world tick; later screens do not suspend the script. */
public final class WorldReadyGate {
    private boolean started;

    public boolean ready(boolean playerAndWorld, boolean screenOpen) {
        if (!playerAndWorld) return false;
        if (!started && !screenOpen) started = true;
        return started;
    }
}
