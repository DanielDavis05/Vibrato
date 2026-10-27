package dev.vibrato.human.motion;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Splits one angular error into a ballistic primary move and a few corrective moves, the way a person's aim does. The
 * primary covers most of the distance. Corrections take the rest, and an overshoot makes the primary pass the target
 * and the corrections bring it back. The amplitudes always sum to the error.
 */
public final class Submovements {

    /**
     * One move: how many ticks it takes and its signed amplitude in degrees.
     */
    public record Step(int ticks, double amplitude) {}

    private static final int MAX_CORRECTIONS = 3;
    private static final double OVERSHOOT_SCALE_PER_DEG = 1.0 / 200;
    private static final double OVERSHOOT_MAX_CHANCE = 0.9;
    private static final double OVERSHOOT_MIN_FRACTION = 0.02;
    private static final double OVERSHOOT_MAX_FRACTION = 0.08;
    private static final double TICKS_PER_SQRT_DEG = 1.2;

    private Submovements() {}

    /**
     * Plans the moves for one turn. Empty when there is no error. Otherwise the returned amplitudes sum to
     * {@code errorDeg}, and the last move ends on the target.
     */
    public static List<Step> plan(double errorDeg, HumanProfile profile, RandomGenerator rng) {
        List<Step> steps = new ArrayList<>();
        if (errorDeg == 0) {
            return steps;
        }
        double overshootChance = Math.min(OVERSHOOT_MAX_CHANCE, profile.overshootScale() * Math.abs(errorDeg) * OVERSHOOT_SCALE_PER_DEG);
        boolean overshoot = rng.nextDouble() < overshootChance;
        int corrections = poisson(profile.correctionCountMean(), rng);

        double primary;
        if (overshoot) {
            double extra = OVERSHOOT_MIN_FRACTION + (OVERSHOOT_MAX_FRACTION - OVERSHOOT_MIN_FRACTION) * rng.nextDouble();
            primary = errorDeg * (1 + extra);
            corrections = Math.max(1, corrections);
        } else if (corrections == 0) {
            primary = errorDeg;
        } else {
            primary = errorDeg * profile.primaryFraction();
        }
        steps.add(new Step(ticksFor(primary, profile), primary));

        double remainder = errorDeg - primary;
        if (corrections > 0 && remainder != 0) {
            double[] weights = new double[corrections];
            double total = 0;
            for (int i = 0; i < corrections; i++) {
                weights[i] = 0.2 + rng.nextDouble();
                total += weights[i];
            }
            double placed = 0;
            for (int i = 0; i < corrections; i++) {
                double amplitude = i == corrections - 1 ? remainder - placed : remainder * weights[i] / total;
                placed += amplitude;
                steps.add(new Step(ticksFor(amplitude, profile), amplitude));
            }
        }
        return steps;
    }

    private static int ticksFor(double amplitude, HumanProfile profile) {
        double ticks = TICKS_PER_SQRT_DEG * Math.sqrt(Math.abs(amplitude)) / profile.turnPeakSpeedScale();
        return (int) Math.max(1, Math.round(ticks));
    }

    /**
     * Poisson count with the given mean, truncated to 0..{@value #MAX_CORRECTIONS}.
     */
    private static int poisson(double mean, RandomGenerator rng) {
        double u = rng.nextDouble();
        double term = Math.exp(-mean);
        double cumulative = term;
        int k = 0;
        while (u > cumulative && k < MAX_CORRECTIONS) {
            k++;
            term *= mean / k;
            cumulative += term;
        }
        return k;
    }
}
