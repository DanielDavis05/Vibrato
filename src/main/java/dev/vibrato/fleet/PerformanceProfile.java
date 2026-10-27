package dev.vibrato.fleet;

import dev.vibrato.mixins.SimpleOptionAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.sound.SoundCategory;

import java.util.Locale;

/**
 * Applies the low-resource option set to the running client. Only the options in {@link OptionsPlan} change, and
 * the user's options.txt is never written.
 */
public final class PerformanceProfile {

    private static final String SOUND_PREFIX = "soundVolume.";

    private PerformanceProfile() {
    }

    public static void apply(MinecraftClient client, FleetConfig config) {
        GameOptions options = client.options;
        for (OptionsPlan.Setting setting : OptionsPlan.forConfig(config).settings()) {
            if (setting.option().equals("pauseOnLostFocus")) {
                options.pauseOnLostFocus = (Boolean) setting.value();
                continue;
            }
            SimpleOption<?> option = option(options, setting.option());
            // Bypasses the slider range so values such as a 0 volume or a 5-chunk render distance always stick.
            ((SimpleOptionAccessor) (Object) option).setRawValue(toRaw(setting.option(), setting.value()));
        }
    }

    public static String describe(FleetConfig config) {
        return String.format(Locale.ROOT,
                "fleet bot=%s seed=%d persona=%s autostart=%s planLength=%d startDelay=%ds server=%s perf=%s dataDir=%s",
                config.botId(), config.seed(),
                config.persona() == null ? "auto" : config.persona(),
                config.autostart(), config.planLength(), config.startDelaySeconds(),
                config.serverAddress() == null ? "none" : config.serverAddress(),
                config.perfMode().name().toLowerCase(Locale.ROOT), config.dataDir());
    }

    private static SimpleOption<?> option(GameOptions options, String name) {
        if (name.startsWith(SOUND_PREFIX)) {
            SoundCategory category = SoundCategory.valueOf(name.substring(SOUND_PREFIX.length()).toUpperCase(Locale.ROOT));
            return options.getSoundVolumeOption(category);
        }
        return switch (name) {
            case "renderDistance" -> options.getViewDistance();
            case "simulationDistance" -> options.getSimulationDistance();
            case "maxFps" -> options.getMaxFps();
            case "enableVsync" -> options.getEnableVsync();
            case "graphicsMode" -> options.getGraphicsMode();
            case "particles" -> options.getParticles();
            case "cloudRenderMode" -> options.getCloudRenderMode();
            case "entityShadows" -> options.getEntityShadows();
            case "bobView" -> options.getBobView();
            case "biomeBlendRadius" -> options.getBiomeBlendRadius();
            case "guiScale" -> options.getGuiScale();
            default -> throw new IllegalArgumentException("Unknown performance option: " + name);
        };
    }

    private static Object toRaw(String option, Object value) {
        if (!(value instanceof String constant)) {
            return value;
        }
        return switch (option) {
            case "graphicsMode" -> GraphicsMode.valueOf(constant);
            case "particles" -> ParticlesMode.valueOf(constant);
            case "cloudRenderMode" -> CloudRenderMode.valueOf(constant);
            default -> throw new IllegalArgumentException("Option " + option + " has no enum mapping");
        };
    }
}
