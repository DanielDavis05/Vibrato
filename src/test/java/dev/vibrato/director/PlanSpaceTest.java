package dev.vibrato.director;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanSpaceTest {

    private static final Fact X = Fact.of("x:ready");

    private static ActivityRegistry largeRegistry() {
        FeatureArea[] areas = FeatureArea.values();
        ActivityRegistry registry = new ActivityRegistry();
        for (int i = 0; i < 60; i++) {
            List<ParamSpec> params = new ArrayList<>();
            params.add(ParamSpec.ints("a", 0, 9));
            params.add(ParamSpec.choice("b", "x", "y", "z"));
            if (i % 2 == 0) {
                params.add(ParamSpec.ints("c", 0, 4));
            }
            registry.register(new SyntheticActivity("big_" + i, areas[i % areas.length], Set.of(), Set.of(), params));
        }
        return registry;
    }

    /** Counts every sequence of {@code remaining} steps by brute force, using the same eligibility rule. */
    private static double exactCount(List<Activity> activities, Set<Fact> facts, int remaining) {
        if (remaining == 0) {
            return 1;
        }
        double total = 0;
        for (Activity activity : activities) {
            if (facts.containsAll(activity.requires())) {
                double variants = 1;
                for (ParamSpec spec : activity.params()) {
                    variants *= spec.variants();
                }
                Set<Fact> next = new HashSet<>(facts);
                next.addAll(activity.provides());
                total += variants * exactCount(activities, next, remaining - 1);
            }
        }
        return total;
    }

    @Test
    void largeRegistryHasAtLeastEightDigitsOfPlansAtLengthEight() {
        double log10 = PlanSpace.estimateLog10(largeRegistry(), 8, Personas.GENERALIST, Set.of(), 500, 7);
        assertTrue(log10 >= 8.0, "log10 was " + log10);
    }

    @Test
    void estimateIsWithinFactorOfTenOfExactCountOnTinyRegistry() {
        SyntheticActivity walk = SyntheticActivity.of("walk", FeatureArea.EXPLORE);
        SyntheticActivity pick = new SyntheticActivity("pick", FeatureArea.GATHER, Set.of(), Set.of(),
                List.of(ParamSpec.choice("item", "stone", "coal", "iron")));
        SyntheticActivity prepare = new SyntheticActivity("prepare", FeatureArea.CRAFT, Set.of(), Set.of(X),
                List.of(ParamSpec.ints("n", 1, 2)));
        SyntheticActivity use = new SyntheticActivity("use", FeatureArea.CRAFT, Set.of(X), Set.of(),
                List.of(ParamSpec.choice("mode", "fast", "slow")));
        List<Activity> activities = List.of(walk, pick, prepare, use);

        ActivityRegistry registry = new ActivityRegistry();
        activities.forEach(registry::register);

        int length = 3;
        double exact = Math.log10(exactCount(activities, Set.of(), length));
        double estimate = PlanSpace.estimateLog10(registry, length, Personas.GENERALIST, Set.of(), 20_000, 11);

        assertTrue(Math.abs(estimate - exact) < 1.0, "exact log10 " + exact + ", estimate " + estimate);
    }

    @Test
    void describeReadsAsScientificNotation() {
        String text = PlanSpace.describe(largeRegistry(), 8, Personas.GENERALIST, Set.of(), 200, 7);
        assertTrue(text.matches("~[0-9.]+e\\d+ distinct plans of length 8"), text);
    }
}
