package dev.vibrato.director;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class FocusedPlanSamplerTest {
    @Test void insertsOnlyPrerequisiteProvidersAndOneRequestedGoal() {
        ActivityRegistry registry = new ActivityRegistry();
        registry.register(SyntheticActivity.of("unrelated", FeatureArea.COMBAT));
        registry.register(SyntheticActivity.of("wood", FeatureArea.GATHER, Set.of(), Set.of(Fact.has("wood"))));
        registry.register(SyntheticActivity.of("chest", FeatureArea.CRAFT, Set.of(Fact.has("wood")), Set.of(Fact.has("chest"))));
        registry.register(new SyntheticActivity("store", FeatureArea.CHEST, Set.of(Fact.has("chest")), Set.of(),
                List.of(ParamSpec.ints("count", 1, 32))));
        PlanSampler sampler = new PlanSampler(registry);
        Plan plan = sampler.sampleGoal(44, "store", Personas.GENERALIST, Set.of(), new CoverageTracker());
        assertEquals(List.of("wood", "chest", "store"), plan.steps().stream().map(s -> s.activity().id()).toList());
        assertEquals(plan.steps().stream().map(PlannedStep::signature).toList(),
                sampler.sampleGoal(44, "store", Personas.GENERALIST, Set.of(), new CoverageTracker())
                        .steps().stream().map(PlannedStep::signature).toList());
        assertEquals(1, sampler.sampleGoal(44, "store", Personas.GENERALIST, Set.of(Fact.has("chest")),
                new CoverageTracker()).steps().size());
    }

    @Test void unknownAndUnsatisfiedGoalsFailWithoutSubstitutingRandomActivity() {
        ActivityRegistry registry = new ActivityRegistry();
        registry.register(SyntheticActivity.of("unrelated", FeatureArea.GATHER));
        registry.register(SyntheticActivity.of("locked", FeatureArea.TRADE, Set.of(Fact.has("missing")), Set.of()));
        PlanSampler sampler = new PlanSampler(registry);
        assertThrows(IllegalArgumentException.class, () -> sampler.sampleGoal(1, "unknown", Personas.GENERALIST,
                Set.of(), new CoverageTracker()));
        assertThrows(IllegalStateException.class, () -> sampler.sampleGoal(1, "locked", Personas.GENERALIST,
                Set.of(), new CoverageTracker()));
    }

    @Test void cyclicAndCooledDownGoalsCannotBeReportedAsPlans() {
        ActivityRegistry registry = new ActivityRegistry();
        registry.register(SyntheticActivity.of("cyclic", FeatureArea.CRAFT, Set.of(Fact.has("x")), Set.of(Fact.has("x"))));
        PlanSampler sampler = new PlanSampler(registry);
        assertThrows(IllegalStateException.class, () -> sampler.sampleGoal(1, "cyclic", Personas.GENERALIST,
                Set.of(), new CoverageTracker()));
        CoverageTracker coverage = new CoverageTracker();
        coverage.recordFailure("cyclic", 3);
        assertThrows(IllegalStateException.class, () -> sampler.sampleGoal(1, "cyclic", Personas.GENERALIST, Set.of(), coverage));
    }
}
