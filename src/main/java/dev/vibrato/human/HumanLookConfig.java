package dev.vibrato.human;

/**
 * Tuning for {@link HumanLook}. Speeds are in degrees per game tick. Overshoot and turn shape come from the
 * {@link dev.vibrato.human.motion.HumanProfile}, not from here.
 *
 * @param maxYawDegPerTick    hard cap on yaw movement per tick
 * @param maxPitchDegPerTick  hard cap on pitch movement per tick
 * @param gain                acceleration limit as a fraction of the max speed per tick, so a full-speed turn is reached in about 1/gain ticks
 * @param arrivalToleranceDeg distance from the target at which a turn counts as finished
 * @param windStrength        scale of the random wind noise on the path, 0 disables it
 */
public record HumanLookConfig(
        double maxYawDegPerTick,
        double maxPitchDegPerTick,
        double gain,
        double arrivalToleranceDeg,
        double windStrength
) {

    public static final HumanLookConfig DEFAULT = new HumanLookConfig(40, 25, 0.35, 0.4, 0.3);
}
