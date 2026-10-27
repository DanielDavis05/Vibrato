package dev.vibrato.human;

/**
 * Tuning for {@link HumanLook}. Speeds are in degrees per game tick.
 *
 * @param maxYawDegPerTick    hard cap on yaw movement per tick
 * @param maxPitchDegPerTick  hard cap on pitch movement per tick
 * @param gain                acceleration limit as a fraction of the max speed per tick, so a full-speed turn is reached in about 1/gain ticks
 * @param arrivalToleranceDeg distance from the target at which a turn counts as finished
 * @param overshootChance     chance that a turn over 60 degrees overshoots its target before correcting
 * @param overshootMaxDeg     largest overshoot past the target
 * @param windStrength        scale of the random wind noise on the path, 0 disables it
 */
public record HumanLookConfig(
        double maxYawDegPerTick,
        double maxPitchDegPerTick,
        double gain,
        double arrivalToleranceDeg,
        double overshootChance,
        double overshootMaxDeg,
        double windStrength
) {

    public static final HumanLookConfig DEFAULT = new HumanLookConfig(40, 25, 0.35, 0.4, 0.25, 3.0, 0.0);
}
