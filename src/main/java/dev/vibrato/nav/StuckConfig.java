package dev.vibrato.nav;

/**
 * Thresholds for {@link StuckDetector}. Windows are counted in samples (one per tick), distances in blocks,
 * angles in degrees.
 */
public record StuckConfig(
        int stationaryTicks, double stationaryMinMove,
        int oscillationTicks, double oscillationMaxNet, int oscillationMinSwitches,
        int spinTicks, double spinMinDegrees, double spinMaxNet,
        int edgeJamTicks,
        int waterTicks, double waterMinMove,
        int lowAirTicks) {

    public static StuckConfig defaults() {
        return new StuckConfig(
                40, 0.3,
                60, 1.0, 6,
                40, 720.0, 0.5,
                15,
                60, 1.0,
                80);
    }

    int windowTicks() {
        return Math.max(stationaryTicks, Math.max(oscillationTicks, Math.max(spinTicks, waterTicks)));
    }
}
