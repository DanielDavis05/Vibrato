package dev.vibrato.director;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Symbolic reachability is necessary; real biome, entity and inventory prerequisites still need runtime proof. */
class FocusedPrerequisiteAuditTest {
    @Test void everyProofSupportedGoalPlansFromFreshSurfaceOrUndergroundSurvival() {
        ActivityRegistry registry = BuiltinActivities.createRegistry();
        PlanSampler sampler = new PlanSampler(registry);
        for (Fact location : List.of(WorldFacts.SURFACE, WorldFacts.UNDERGROUND)) {
            Set<Fact> fresh = Set.of(Fact.dimension("overworld"), location);
            for (String goal : FocusedActivityTask.supportedIds()) {
                for (long seed : new long[]{0, 1, 7, 424242}) {
                    String context = goal + " seed=" + seed + " location=" + location;
                    Plan sampled = assertDoesNotThrow(() -> sampler.sampleGoal(seed, goal, Personas.GENERALIST,
                            fresh, new CoverageTracker()), context);
                    Plan plan = assertDoesNotThrow(() -> FocusedActivityParameters.constrain(sampled, goal, seed), context);
                    assertEquals(goal, plan.steps().getLast().activity().id(), context);
                    assertEquals(1, plan.steps().stream().filter(step -> step.activity().id().equals(goal)).count(), context);
                    Set<Fact> facts = new HashSet<>(fresh);
                    for (PlannedStep step : plan.steps()) {
                        assertTrue(facts.containsAll(step.activity().requires()), context + " unresolved step " + step.activity().id());
                        PlanSampler.applyEffects(facts, step.activity());
                    }
                }
            }
        }
    }
    @Test void tradingAndFishingUseActualRegisteredProvidersInsteadOfInventedFacts() {
        ActivityRegistry registry = BuiltinActivities.createRegistry();
        Set<Fact> fresh = Set.of(Fact.dimension("overworld"), WorldFacts.SURFACE);
        for (String goal : List.of("trade_for_item", "trade_for_enchanted_book")) {
            var plan = new PlanSampler(registry).sampleGoal(424242, goal, Personas.GENERALIST, fresh, new CoverageTracker());
            assertTrue(plan.steps().stream().anyMatch(step -> step.activity().id().equals("travel_find_village")));
            assertEquals(Set.of(Fact.of("village:found")), registry.get(goal).requires());
        }
        var fish = new PlanSampler(registry).sampleGoal(424242, "fish", Personas.GENERALIST, fresh, new CoverageTracker());
        assertEquals(List.of("gather_string", "craft_fishing_rod", "fish"), fish.steps().stream().map(step -> step.activity().id()).toList());
    }
}
