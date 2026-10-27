package dev.vibrato.human;

import baritone.api.Settings;
import baritone.vibrato.VibratoSettings;
import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.human.motion.HumanProfile;
import dev.vibrato.human.motion.Population;
import dev.vibrato.human.wire.HumanKeys;
import dev.vibrato.human.wire.ShapingLayer;
import net.minecraft.client.MinecraftClient;

import java.util.SplittableRandom;

/**
 * Connects {@link HumanLook} to Baritone's aim and to the non-Baritone look helpers, and the key layer to the player's
 * movement input. One {@link HumanProfile} per bot is sampled from the seed and shared by both.
 */
public final class Human {

    private static final long KEY_SEED_SALT = 0x4B455953L;

    private static volatile HumanLook look;

    private Human() {}

    /**
     * Installs the camera model into Baritone and turns off its own jitter and smoothing, which the model replaces.
     * {@code -Dvibrato.human=off} skips all of it and leaves Baritone's stock aiming, for A/B comparisons.
     * {@code -Dvibrato.humanKeys=off} skips only the key layer; {@code -Dvibrato.debugKeys=true} logs shaped keys.
     */
    public static void install(Vibrato mod, long seed) {
        if ("off".equalsIgnoreCase(System.getProperty("vibrato.human"))) {
            Debug.logMessage("Human camera: OFF (-Dvibrato.human=off), using Baritone's stock aiming");
            return;
        }
        Debug.logMessage("Human camera: ON");
        HumanProfile profile = HumanProfile.sample(new SplittableRandom(seed), Population.load());
        Debug.logMessage("Human profile: " + profile);
        HumanLook humanLook = new HumanLook(seed, HumanLookConfig.DEFAULT, profile);
        humanLook.setSensitivitySource(() -> MinecraftClient.getInstance().options.getMouseSensitivity().getValue());
        look = humanLook;
        VibratoSettings.getInstance().setRotationSmoother(humanLook);

        Settings settings = mod.getClientBaritoneSettings();
        settings.randomLooking.value = 0.0;
        settings.randomLooking113.value = 0.0;
        settings.smoothLook.value = false;
        settings.freeLook.value = false;

        if ("off".equalsIgnoreCase(System.getProperty("vibrato.humanKeys"))) {
            Debug.logMessage("Human keys: OFF (-Dvibrato.humanKeys=off), Baritone's keys pass through");
        } else {
            Debug.logMessage("Human keys: ON");
            // A separate stream from the profile's, so the same seed still gives the same profile.
            HumanKeys.install(new ShapingLayer(seed ^ KEY_SEED_SALT, profile),
                    Boolean.getBoolean("vibrato.debugKeys"));
        }
    }

    /**
     * @return the installed camera model, or {@code null} when {@link #install} has not run
     */
    public static HumanLook look() {
        return look;
    }
}
