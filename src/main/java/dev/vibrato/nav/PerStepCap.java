package dev.vibrato.nav;

/** Caps how often one action may fire within a single step. A new step serial starts a fresh count. */
public final class PerStepCap {

    private final int _cap;
    private int _serial = Integer.MIN_VALUE;
    private int _uses;

    public PerStepCap(int cap) {
        _cap = cap;
    }

    /** @return true and counts the use if this step still has uses left */
    public boolean tryUse(int stepSerial) {
        if (stepSerial != _serial) {
            _serial = stepSerial;
            _uses = 0;
        }
        if (_uses >= _cap) {
            return false;
        }
        _uses++;
        return true;
    }
}
