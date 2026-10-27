package dev.vibrato.human.motion;

import dev.vibrato.human.motion.Population.Trait;

import java.util.random.RandomGenerator;

/**
 * One bot's human traits, a single draw from a {@link Population}. Each field is documented by its trait in
 * {@link Trait}. Times are in game ticks, rates are per tick or per planned event as noted there.
 *
 * @param reactionMedianTicks    median delay before reacting to a new stimulus
 * @param jumpTimingBiasTicks    systematic jump timing offset, negative is early
 * @param jumpTimingSigmaTicks   jitter of the jump timing around the bias
 * @param jumpMissChance         chance a planned jump is skipped on the first try
 * @param strafeTapRate          per-tick chance of a strafe tap while strafing
 * @param keyHoldMedianTicks     median length of a strafe tap break
 * @param keyHoldSigma           log-normal spread of tap break lengths
 * @param hesitationRate         per-tick chance of a short all-keys-up pause while moving
 * @param lateralSigma           per-tick noise of the lateral path offset, in blocks
 * @param lateralReversion       per-tick pull of the lateral offset back to the centreline
 * @param lookAheadBlocks        distance along the path of the aim point
 * @param primaryFraction        share of a turn covered by the primary submovement
 * @param correctionCountMean    mean number of corrective submovements per turn
 * @param overshootScale         multiplier on the overshoot chance of a turn
 * @param turnPeakSpeedScale     multiplier on turn speed, above 1 is faster
 */
public record HumanProfile(
        double reactionMedianTicks,
        double jumpTimingBiasTicks,
        double jumpTimingSigmaTicks,
        double jumpMissChance,
        double strafeTapRate,
        double keyHoldMedianTicks,
        double keyHoldSigma,
        double hesitationRate,
        double lateralSigma,
        double lateralReversion,
        double lookAheadBlocks,
        double primaryFraction,
        double correctionCountMean,
        double overshootScale,
        double turnPeakSpeedScale
) {

    /**
     * Draws one profile. The same seed and population always give the same profile.
     */
    public static HumanProfile sample(RandomGenerator rng, Population population) {
        return new HumanProfile(
                population.draw(Trait.REACTION_MEDIAN_TICKS, rng),
                population.draw(Trait.JUMP_TIMING_BIAS_TICKS, rng),
                population.draw(Trait.JUMP_TIMING_SIGMA_TICKS, rng),
                population.draw(Trait.JUMP_MISS_CHANCE, rng),
                population.draw(Trait.STRAFE_TAP_RATE, rng),
                population.draw(Trait.KEY_HOLD_MEDIAN_TICKS, rng),
                population.draw(Trait.KEY_HOLD_SIGMA, rng),
                population.draw(Trait.HESITATION_RATE, rng),
                population.draw(Trait.LATERAL_SIGMA, rng),
                population.draw(Trait.LATERAL_REVERSION, rng),
                population.draw(Trait.LOOK_AHEAD_BLOCKS, rng),
                population.draw(Trait.PRIMARY_FRACTION, rng),
                population.draw(Trait.CORRECTION_COUNT, rng),
                population.draw(Trait.OVERSHOOT_SCALE, rng),
                population.draw(Trait.TURN_PEAK_SPEED_SCALE, rng)
        );
    }

    /**
     * The population means with no randomness, for tests and for a deterministic average player.
     */
    public static HumanProfile typical(Population population) {
        return new HumanProfile(
                population.mean(Trait.REACTION_MEDIAN_TICKS),
                population.mean(Trait.JUMP_TIMING_BIAS_TICKS),
                population.mean(Trait.JUMP_TIMING_SIGMA_TICKS),
                population.mean(Trait.JUMP_MISS_CHANCE),
                population.mean(Trait.STRAFE_TAP_RATE),
                population.mean(Trait.KEY_HOLD_MEDIAN_TICKS),
                population.mean(Trait.KEY_HOLD_SIGMA),
                population.mean(Trait.HESITATION_RATE),
                population.mean(Trait.LATERAL_SIGMA),
                population.mean(Trait.LATERAL_REVERSION),
                population.mean(Trait.LOOK_AHEAD_BLOCKS),
                population.mean(Trait.PRIMARY_FRACTION),
                population.mean(Trait.CORRECTION_COUNT),
                population.mean(Trait.OVERSHOOT_SCALE),
                population.mean(Trait.TURN_PEAK_SPEED_SCALE)
        );
    }
}
