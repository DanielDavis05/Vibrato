package dev.vibrato.director;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreActivitiesTest {

    private static ActivityRegistry coreRegistry() {
        ActivityRegistry registry = new ActivityRegistry();
        CoreActivities.register(registry);
        return registry;
    }

    @Test
    void registersAtLeastThirtyUniqueActivities() {
        List<Activity> activities = coreRegistry().all();

        assertTrue(activities.size() >= 30, "only " + activities.size() + " activities");
        Set<String> ids = new HashSet<>();
        activities.forEach(activity -> ids.add(activity.id()));
        assertEquals(activities.size(), ids.size());
    }

    @Test
    void coversTheCoreFeatureAreas() {
        Set<FeatureArea> areas = new HashSet<>();
        coreRegistry().all().forEach(activity -> areas.add(activity.area()));

        assertTrue(areas.containsAll(Set.of(FeatureArea.GATHER, FeatureArea.CRAFT, FeatureArea.SMELT, FeatureArea.GEAR,
                FeatureArea.INVENTORY, FeatureArea.CHEST, FeatureArea.FARM, FeatureArea.EXPLORE,
                FeatureArea.TRANSPORT, FeatureArea.SLEEP)), areas.toString());
    }

    @Test
    void everyBudgetIsPositiveForSampledParameters() {
        Random random = new Random(3);
        for (Activity activity : coreRegistry().all()) {
            for (int i = 0; i < 20; i++) {
                ParamValues values = PlanSampler.sampleParams(activity, random);
                Duration budget = activity.budget(values);
                assertTrue(budget.compareTo(Duration.ZERO) > 0, activity.id() + " budget " + budget);
            }
        }
    }

    @Test
    void tenStepPlansAreAmpleEnough() {
        double log10 = PlanSpace.estimateLog10(coreRegistry(), 10, Personas.GENERALIST, Set.of(), 300, 1);

        assertTrue(log10 >= 8.0, "log10 was " + log10);
    }
}
