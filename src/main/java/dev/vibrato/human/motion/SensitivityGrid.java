package dev.vibrato.human.motion;

/**
 * The vanilla mouse-sensitivity grid. One mouse pixel turns the camera by a fixed step that depends on the sensitivity
 * setting, so every real turn is a whole number of pixels. Quantizing to that grid makes the bot's camera movement
 * match what a mouse can produce.
 */
public final class SensitivityGrid {

    private SensitivityGrid() {}

    /**
     * Degrees of camera turn per mouse pixel: {@code (sens*0.6+0.2)^3 * 8 * 0.15}.
     *
     * @param sensitivity mouse sensitivity in [0, 1], the slider value
     */
    public static double degreesPerPixel(double sensitivity) {
        if (!(sensitivity >= 0 && sensitivity <= 1)) {
            throw new IllegalArgumentException("sensitivity must be in [0, 1]: " + sensitivity);
        }
        double f = sensitivity * 0.6 + 0.2;
        return f * f * f * 8 * 0.15;
    }

    /**
     * The whole number of pixels nearest to {@code degrees}.
     */
    public static long pixels(double degrees, double sensitivity) {
        return Math.round(degrees / degreesPerPixel(sensitivity));
    }

    /**
     * {@code degrees} snapped to the nearest grid step. The result is always an integer multiple of
     * {@link #degreesPerPixel}.
     */
    public static double quantize(double degrees, double sensitivity) {
        return pixels(degrees, sensitivity) * degreesPerPixel(sensitivity);
    }
}
