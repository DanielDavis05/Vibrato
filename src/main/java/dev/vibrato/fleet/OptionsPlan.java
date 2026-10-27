package dev.vibrato.fleet;

import dev.vibrato.fleet.FleetConfig.PerfMode;

import java.util.List;

/**
 * The option values a bot client should run with, as plain data so they can be tested without Minecraft.
 * Enum-valued options are given by constant name; {@link PerformanceProfile} converts them.
 */
public record OptionsPlan(List<Setting> settings) {

    public record Setting(String option, Object value) {
    }

    public static OptionsPlan forConfig(FleetConfig config) {
        if (config.perfMode() == PerfMode.NORMAL) {
            return new OptionsPlan(List.of());
        }
        return new OptionsPlan(List.of(
                new Setting("renderDistance", 5),
                new Setting("simulationDistance", 5),
                new Setting("maxFps", 30),
                new Setting("enableVsync", false),
                new Setting("graphicsMode", "FAST"),
                new Setting("particles", "MINIMAL"),
                new Setting("cloudRenderMode", "OFF"),
                new Setting("entityShadows", false),
                new Setting("bobView", false),
                new Setting("biomeBlendRadius", 0),
                new Setting("guiScale", 1),
                new Setting("pauseOnLostFocus", false),
                new Setting("soundVolume.master", 0.0),
                new Setting("soundVolume.music", 0.0),
                new Setting("soundVolume.ambient", 0.0)
        ));
    }
}
