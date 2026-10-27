package dev.vibrato.nav;

/** A bank needs a dry standing space and must be within one step of the swimmer's feet. */
final class BankCandidate {
    private BankCandidate() {}

    static boolean usable(int groundRelativeY, boolean solidGround, boolean clearFeet, boolean clearHead,
                          boolean dryFeet, boolean dryHead) {
        return groundRelativeY >= -2 && groundRelativeY <= 0 && solidGround
                && clearFeet && clearHead && dryFeet && dryHead;
    }
}
