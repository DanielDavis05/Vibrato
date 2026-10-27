package dev.vibrato.human;

import baritone.api.Settings;
import baritone.vibrato.VibratoSettings;
import dev.vibrato.Vibrato;

/**
 * Connects {@link HumanLook} to Baritone's aim and to the non-Baritone look helpers.
 */
public final class Human {

    private static volatile HumanLook look;

    private Human() {}

    /**
     * Installs the camera model into Baritone and turns off its own jitter and smoothing, which the model replaces.
     */
    public static void install(Vibrato mod, long seed) {
        HumanLook humanLook = new HumanLook(seed, HumanLookConfig.DEFAULT);
        look = humanLook;
        VibratoSettings.getInstance().setRotationSmoother(humanLook);

        Settings settings = mod.getClientBaritoneSettings();
        settings.randomLooking.value = 0.0;
        settings.randomLooking113.value = 0.0;
        settings.smoothLook.value = false;
        settings.freeLook.value = false;
    }

    /**
     * @return the installed camera model, or {@code null} when {@link #install} has not run
     */
    public static HumanLook look() {
        return look;
    }
}
