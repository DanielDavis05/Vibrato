package dev.vibrato.director;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Estimates how many distinct plans of a given length exist, using Knuth's tree-size estimator over random walks.
 * <p>
 * Each walk picks an eligible activity by persona weight w (total W over eligible ones), then its parameters uniformly,
 * so a child is chosen with probability (w / W) / V, V being the chosen activity's parameter variants. Weighting the
 * walk by W * V / w at each level keeps the mean unbiased for the number of plans. Walks are summed in log space.
 * <p>
 * Eligibility is only the {@code requires} check; the no-repeat and cooldown rules of {@link PlanSampler} are ignored.
 */
public final class PlanSpace {

    private static final double LN10 = Math.log(10);
    private static final String ALGORITHM = "L64X128MixRandom";

    private PlanSpace() {}

    /** @return log10 of the estimated number of plans of {@code length} steps */
    public static double estimateLog10(ActivityRegistry registry, int length, Persona persona, Set<Fact> startFacts,
                                       int samples, long seed) {
        if (samples < 1) {
            throw new IllegalArgumentException("samples must be at least 1");
        }
        RandomGenerator rng = RandomGeneratorFactory.of(ALGORITHM).create(seed);
        double[] logWeights = new double[samples];
        for (int sample = 0; sample < samples; sample++) {
            logWeights[sample] = walk(registry, length, persona, startFacts, rng);
        }
        return (logSumExp(logWeights) - Math.log(samples)) / LN10;
    }

    /** Human-readable form, e.g. {@code "~1.2e23 distinct plans of length 10"}. */
    public static String describe(ActivityRegistry registry, int length, Persona persona, Set<Fact> startFacts,
                                  int samples, long seed) {
        double log10 = estimateLog10(registry, length, persona, startFacts, samples, seed);
        if (Double.isInfinite(log10)) {
            return "no reachable plans of length " + length;
        }
        if (log10 < 3) {
            return String.format(Locale.ROOT, "~%d distinct plans of length %d", Math.round(Math.pow(10, log10)), length);
        }
        int exponent = (int) Math.floor(log10);
        double mantissa = Math.pow(10, log10 - exponent);
        return String.format(Locale.ROOT, "~%.1fe%d distinct plans of length %d", mantissa, exponent, length);
    }

    /** @return log of the walk's weight, or -infinity if the walk hit a dead end */
    private static double walk(ActivityRegistry registry, int length, Persona persona, Set<Fact> startFacts,
                               RandomGenerator rng) {
        Set<Fact> facts = new HashSet<>(startFacts);
        double logWeight = 0;
        for (int step = 0; step < length; step++) {
            List<Activity> eligible = eligible(registry, facts);
            if (eligible.isEmpty()) {
                return Double.NEGATIVE_INFINITY;
            }
            double[] weights = new double[eligible.size()];
            double total = 0;
            for (int i = 0; i < weights.length; i++) {
                weights[i] = eligible.get(i).weight(persona);
                total += weights[i];
            }
            int index = pickIndex(weights, total, rng);
            Activity chosen = eligible.get(index);
            logWeight += Math.log(total / weights[index]) + logVariants(chosen);
            PlanSampler.applyEffects(facts, chosen);
        }
        return logWeight;
    }

    private static int pickIndex(double[] weights, double total, RandomGenerator rng) {
        double roll = rng.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    private static List<Activity> eligible(ActivityRegistry registry, Set<Fact> facts) {
        List<Activity> result = new ArrayList<>();
        for (Activity activity : registry.all()) {
            if (facts.containsAll(activity.requires())) {
                result.add(activity);
            }
        }
        return result;
    }

    private static double logVariants(Activity activity) {
        double total = 0;
        for (ParamSpec spec : activity.params()) {
            total += Math.log(spec.variants());
        }
        return total;
    }

    private static double logSumExp(double[] values) {
        double max = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            max = Math.max(max, value);
        }
        if (Double.isInfinite(max)) {
            return max;
        }
        double sum = 0;
        for (double value : values) {
            sum += Math.exp(value - max);
        }
        return max + Math.log(sum);
    }
}
