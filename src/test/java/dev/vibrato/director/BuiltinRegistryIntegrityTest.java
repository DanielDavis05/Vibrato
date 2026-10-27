package dev.vibrato.director;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the whole catalogue of every domain, not just one workstream's. */
class BuiltinRegistryIntegrityTest {

    /** Facts the world provides at a plan boundary, so they need no provider. */
    private static final Set<Fact> WORLD_FACTS = new HashSet<>(Set.of(
            Fact.dimension("overworld"), Fact.dimension("nether"), Fact.dimension("end"),
            WorldFacts.SURFACE, WorldFacts.UNDERGROUND));
    static {
        WORLD_FACTS.addAll(WorldFacts.rareItemFacts());
    }

    /**
     * Facts that the T3 transport and exploration domain must provide: no village-finding task exists yet, so
     * villager trading stays unschedulable until it does. Remove an entry once a provider is registered.
     */
    private static final Set<Fact> KNOWN_UNPROVIDED = Set.of();

    /** Areas whose domain is still thin on master (combat is T6, command is T5). Remove entries as they fill up. */
    private static final Set<FeatureArea> KNOWN_THIN = Set.of(FeatureArea.COMBAT, FeatureArea.COMMAND);

    private static final Set<Fact> START_FACTS = Set.of(Fact.dimension("overworld"), WorldFacts.SURFACE);

    private static ActivityRegistry builtRegistry() {
        return assertDoesNotThrow(BuiltinActivities::createRegistry);
    }

    /** Once a fact gets a provider, it must leave KNOWN_UNPROVIDED, so the list cannot rot. */
    @Test
    void knownUnprovidedFactsStillHaveNoProvider() {
        ActivityRegistry registry = builtRegistry();
        for (Fact fact : KNOWN_UNPROVIDED) {
            assertTrue(registry.providersOf(fact).isEmpty(), fact + " is now provided; remove it from KNOWN_UNPROVIDED");
        }
    }

    @Test
    void xpActivitiesRequireBasicToolsAndRemainPlannable() {
        ActivityRegistry registry = builtRegistry();
        for (String id : List.of("gain_xp_levels", "level_10", "level_20", "level_30", "collect_xp_levels")) {
            Activity activity = registry.get(id);
            assertTrue(activity.requires().contains(WorldFacts.BASIC_PICKAXE), id);
            assertTrue(!PlanSampler.canRunNow(activity, START_FACTS, new CoverageTracker()), id);
        }
        assertTrue(registry.providersOf(WorldFacts.BASIC_PICKAXE).stream()
                .anyMatch(activity -> activity.id().equals("craft_stone_pickaxe")));
    }

    @Test
    void idsAreUnique() {
        ActivityRegistry registry = builtRegistry();
        Set<String> ids = new HashSet<>();
        for (Activity activity : registry.all()) {
            assertTrue(ids.add(activity.id()), "duplicate id " + activity.id());
        }
        assertEquals(registry.size(), ids.size());
    }

    @Test
    void everyRequirementHasAProviderOrIsAWorldFact() {
        ActivityRegistry registry = builtRegistry();
        for (Activity activity : registry.all()) {
            for (Fact fact : activity.requires()) {
                assertTrue(WORLD_FACTS.contains(fact) || KNOWN_UNPROVIDED.contains(fact) || !registry.providersOf(fact).isEmpty(),
                        activity.id() + " requires " + fact + " but nothing provides it");
            }
        }
    }

    @Test
    void everyFeatureAreaHasAtLeastThreeActivities() {
        ActivityRegistry registry = builtRegistry();
        Map<FeatureArea, Integer> counts = new EnumMap<>(FeatureArea.class);
        for (Activity activity : registry.all()) {
            counts.merge(activity.area(), 1, Integer::sum);
        }
        List<String> thin = new ArrayList<>();
        for (FeatureArea area : FeatureArea.values()) {
            if (!KNOWN_THIN.contains(area) && counts.getOrDefault(area, 0) < 3) {
                thin.add(area + "=" + counts.getOrDefault(area, 0));
            }
        }
        assertTrue(thin.isEmpty(), "areas with fewer than 3 activities: " + thin);
    }

    @Test
    void everyParameterHasAtLeastOneVariantAndBudgetsAreOneToThirtyMinutes() {
        ActivityRegistry registry = builtRegistry();
        Random random = new Random(7);
        for (Activity activity : registry.all()) {
            for (ParamSpec spec : activity.params()) {
                assertTrue(spec.variants() >= 1, activity.id() + "." + spec.name() + " has no variants");
            }
            for (int i = 0; i < 5; i++) {
                ParamValues values = PlanSampler.sampleParams(activity, random);
                Duration budget = activity.budget(values);
                assertTrue(budget.compareTo(Duration.ofMinutes(1)) >= 0 && budget.compareTo(Duration.ofMinutes(30)) <= 0,
                        activity.id() + " budget " + budget);
            }
        }
    }

    @Test
    void coverageSimulationExercisesEveryFeatureAreaForEveryPersona() {
        ActivityRegistry registry = builtRegistry();
        PlanSampler sampler = new PlanSampler(registry);
        Set<FeatureArea> seen = new HashSet<>();
        for (Persona persona : Personas.all()) {
            CoverageTracker coverage = new CoverageTracker();
            for (long seed = 1; seed <= 25; seed++) {
                Plan plan = sampler.sample(seed, 10, persona, START_FACTS, coverage);
                for (PlannedStep step : plan.steps()) {
                    seen.add(step.activity().area());
                    coverage.record(step.activity());
                }
            }
        }
        List<FeatureArea> missing = new ArrayList<>();
        for (FeatureArea area : FeatureArea.values()) {
            if (!seen.contains(area)) {
                missing.add(area);
            }
        }
        assertTrue(missing.isEmpty(), "never exercised: " + missing);
    }

    @Test
    void planSpaceIsLargeAtLengthTen() {
        double log10 = PlanSpace.estimateLog10(builtRegistry(), 10, Personas.GENERALIST, START_FACTS, 400, 1);
        System.out.println("PlanSpace log10 estimate for length 10 (full registry): " + log10);
        assertTrue(log10 >= 8.0, "log10 was " + log10);
    }
}
