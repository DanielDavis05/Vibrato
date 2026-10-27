package dev.vibrato.director;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanSamplerTest {

    private static final Fact PLANKS = Fact.has("planks");

    @Test
    void preplannedStepsAreRecheckedAfterFailureAndCooldown() {
        Activity provider = SyntheticActivity.of("gather_planks", FeatureArea.GATHER, Set.of(), Set.of(PLANKS));
        Activity consumer = SyntheticActivity.of("craft_pick", FeatureArea.CRAFT, Set.of(PLANKS), Set.of());
        Activity unrelated = SyntheticActivity.of("explore", FeatureArea.EXPLORE);
        CoverageTracker coverage = new CoverageTracker();
        Set<Fact> facts = new HashSet<>();

        // The provider failed: its effects were never applied, but independent work remains eligible.
        coverage.recordFailure(provider.id(), 3);
        assertFalse(PlanSampler.canRunNow(provider, facts, coverage));
        assertFalse(PlanSampler.canRunNow(consumer, facts, coverage));
        assertTrue(PlanSampler.canRunNow(unrelated, facts, coverage));

        // Once the requirement really exists the dependent step is eligible again.
        PlanSampler.applyEffects(facts, provider);
        assertTrue(PlanSampler.canRunNow(consumer, facts, coverage));
        coverage.recordFailure(consumer.id(), 3);
        assertFalse(PlanSampler.canRunNow(consumer, facts, coverage));
    }

    private static ActivityRegistry registry(Activity... activities) {
        ActivityRegistry registry = new ActivityRegistry();
        for (Activity activity : activities) {
            registry.register(activity);
        }
        return registry;
    }

    private static ActivityRegistry generalRegistry() {
        FeatureArea[] areas = FeatureArea.values();
        ActivityRegistry registry = new ActivityRegistry();
        for (int i = 0; i < 20; i++) {
            registry.register(SyntheticActivity.of("act_" + i, areas[i % areas.length]));
        }
        return registry;
    }

    private static void assertPrerequisitesHold(Plan plan, Set<Fact> startFacts) {
        Set<Fact> facts = new HashSet<>(startFacts);
        for (PlannedStep step : plan.steps()) {
            assertTrue(facts.containsAll(step.activity().requires()), "unmet requirements for " + step.signature());
            facts.addAll(step.activity().provides());
        }
    }

    @Test
    void equalSeedGivesEqualPlan() {
        PlanSampler sampler = new PlanSampler(generalRegistry());
        Plan first = sampler.sample(42, 10, Personas.GENERALIST, Set.of(), new CoverageTracker());
        Plan second = sampler.sample(42, 10, Personas.GENERALIST, Set.of(), new CoverageTracker());

        assertEquals(10, first.steps().size());
        assertEquals(first.signature(), second.signature());
    }

    @Test
    void differentSeedsGiveDifferentPlans() {
        PlanSampler sampler = new PlanSampler(generalRegistry());
        Set<String> signatures = new HashSet<>();
        for (long seed = 1; seed <= 20; seed++) {
            signatures.add(sampler.sample(seed, 10, Personas.GENERALIST, Set.of(), new CoverageTracker()).signature());
        }
        assertTrue(signatures.size() > 15, "expected mostly distinct plans, got " + signatures.size());
    }

    @Test
    void insertsProviderBeforeCandidate() {
        ActivityRegistry registry = registry(
                SyntheticActivity.of("craft_pick", FeatureArea.CRAFT, Set.of(PLANKS), Set.of(Fact.has("pickaxe"))),
                SyntheticActivity.of("gather_planks", FeatureArea.GATHER, Set.of(), Set.of(PLANKS)));
        PlanSampler sampler = new PlanSampler(registry);

        int withPickaxe = 0;
        for (long seed = 1; seed <= 100; seed++) {
            Plan plan = sampler.sample(seed, 1, Personas.GENERALIST, Set.of(), new CoverageTracker());
            assertPrerequisitesHold(plan, Set.of());
            if (plan.steps().stream().anyMatch(step -> step.activity().id().equals("craft_pick"))) {
                withPickaxe++;
                assertEquals("gather_planks", plan.steps().get(0).activity().id(), plan.signature());
            }
        }
        assertTrue(withPickaxe > 0, "craft_pick was never chosen");
    }

    @Test
    void providerChainsAreFilledRecursively() {
        ActivityRegistry registry = registry(
                SyntheticActivity.of("make_door", FeatureArea.CRAFT, Set.of(Fact.has("wood")), Set.of(Fact.has("door"))),
                SyntheticActivity.of("chop_tree", FeatureArea.GATHER, Set.of(Fact.has("axe")), Set.of(Fact.has("wood"))),
                SyntheticActivity.of("make_axe", FeatureArea.CRAFT, Set.of(), Set.of(Fact.has("axe"))));
        PlanSampler sampler = new PlanSampler(registry);

        for (long seed = 1; seed <= 50; seed++) {
            Plan plan = sampler.sample(seed, 1, Personas.GENERALIST, Set.of(), new CoverageTracker());
            assertPrerequisitesHold(plan, Set.of());
            assertTrue(plan.steps().size() <= 3, plan.signature());
        }
    }

    @Test
    void neverRepeatsSameActivityInARow() {
        ActivityRegistry registry = registry(
                SyntheticActivity.of("a", FeatureArea.GATHER),
                SyntheticActivity.of("b", FeatureArea.CRAFT),
                SyntheticActivity.of("c", FeatureArea.FARM));
        PlanSampler sampler = new PlanSampler(registry);

        for (long seed = 1; seed <= 20; seed++) {
            Plan plan = sampler.sample(seed, 50, Personas.GENERALIST, Set.of(), new CoverageTracker());
            List<PlannedStep> steps = plan.steps();
            for (int i = 1; i < steps.size(); i++) {
                assertNotEquals(steps.get(i - 1).activity().id(), steps.get(i).activity().id(), plan.signature());
            }
        }
    }

    @Test
    void cooldownExcludesActivityUntilItExpires() {
        ActivityRegistry registry = registry(
                SyntheticActivity.of("a", FeatureArea.GATHER),
                SyntheticActivity.of("b", FeatureArea.CRAFT),
                SyntheticActivity.of("c", FeatureArea.FARM));
        PlanSampler sampler = new PlanSampler(registry);
        CoverageTracker coverage = new CoverageTracker();
        coverage.recordFailure("a", 3);

        for (long seed = 1; seed <= 30; seed++) {
            Plan plan = sampler.sample(seed, 10, Personas.GENERALIST, Set.of(), coverage);
            assertTrue(plan.steps().stream().noneMatch(step -> step.activity().id().equals("a")), plan.signature());
        }

        coverage.tickPlan();
        coverage.tickPlan();
        coverage.tickPlan();
        assertFalse(coverage.isOnCooldown("a"));
        boolean chosenAgain = IntStream.rangeClosed(1, 30).anyMatch(seed ->
                sampler.sample(seed, 10, Personas.GENERALIST, Set.of(), coverage).steps().stream()
                        .anyMatch(step -> step.activity().id().equals("a")));
        assertTrue(chosenAgain);
    }

    @Test
    void activityWithoutProviderIsSkipped() {
        ActivityRegistry registry = registry(
                SyntheticActivity.of("needs_unicorn", FeatureArea.CRAFT, Set.of(Fact.has("unicorn")), Set.of()),
                SyntheticActivity.of("walk", FeatureArea.EXPLORE),
                SyntheticActivity.of("swim", FeatureArea.TRANSPORT));
        PlanSampler sampler = new PlanSampler(registry);

        for (long seed = 1; seed <= 20; seed++) {
            Plan plan = sampler.sample(seed, 5, Personas.GENERALIST, Set.of(), new CoverageTracker());
            assertTrue(plan.steps().stream().noneMatch(step -> step.activity().id().equals("needs_unicorn")), plan.signature());
        }
    }

    @Test
    void throwsWhenNothingIsSatisfiable() {
        ActivityRegistry registry = registry(
                SyntheticActivity.of("needs_unicorn", FeatureArea.CRAFT, Set.of(Fact.has("unicorn")), Set.of()));
        PlanSampler sampler = new PlanSampler(registry);

        assertThrows(IllegalStateException.class,
                () -> sampler.sample(1, 3, Personas.GENERALIST, Set.of(), new CoverageTracker()));
    }
}
