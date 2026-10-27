package dev.vibrato.human.record;

/**
 * Maps the player's effective movement axes to the key flags written to traces. Pure, with no Minecraft types, so it
 * can be unit-tested.
 * <p>
 * Sign convention, matching vanilla {@code KeyboardInput}: {@code forward > 0} is the forward key, {@code forward < 0}
 * the back key, {@code sideways > 0} the left key and {@code sideways < 0} the right key.
 */
final class InputKeys {

    /**
     * Axis magnitudes at or below this count as released. Sneaking and item use scale the axes down (to 0.3 and 0.2 in
     * vanilla), so this stays well below those values while still ignoring float noise.
     */
    static final float DEADZONE = 0.05f;

    private InputKeys() {}

    /** Which movement keys are down. */
    record Pressed(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak) {}

    static Pressed fromAxes(float forward, float sideways, boolean jumping, boolean sneaking) {
        return new Pressed(
                forward > DEADZONE,
                forward < -DEADZONE,
                sideways > DEADZONE,
                sideways < -DEADZONE,
                jumping,
                sneaking);
    }
}
