package dev.vibrato.director;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuildActivitiesTest {

    private static ActivityRegistry registry() {
        ActivityRegistry registry = new ActivityRegistry();
        BuildActivities.register(registry);
        return registry;
    }

    @Test
    void registersAtLeastTwelveActivitiesAcrossAreas() {
        ActivityRegistry registry = registry();
        assertTrue(registry.size() >= 12, "only " + registry.size());
        assertFalse(registry.inArea(FeatureArea.BUILD).isEmpty());
        assertFalse(registry.inArea(FeatureArea.CHEST).isEmpty());
        assertFalse(registry.inArea(FeatureArea.INVENTORY).isEmpty());
        assertFalse(registry.inArea(FeatureArea.GATHER).isEmpty());
    }

    @Test
    void provisionsUseCanonicalFactShape() {
        for (Activity activity : registry().all()) {
            assertTrue(activity.id().matches("[a-z][a-z0-9_]*"), activity.id());
            for (Fact fact : activity.provides()) {
                assertTrue(fact.key().matches("[a-z_]+:[a-z0-9_]+"), activity.id() + " provides " + fact);
            }
        }
    }

    @Test
    void portalFrameProvidesPortalAndRequiresDiamondTool() {
        Activity portal = registry().get("build_portal_frame");
        assertEquals(Set.of(Fact.of("portal:built")), portal.provides());
        assertEquals(Set.of(Fact.of("tool:diamond")), portal.requires());
    }

    @Test
    void sampledBudgetsStayInsideFourToTwentyFiveMinutes() {
        Random rng = new Random(7);
        for (Activity activity : registry().all()) {
            for (int i = 0; i < 40; i++) {
                Map<String, Object> sampled = new HashMap<>();
                activity.params().forEach(spec -> sampled.put(spec.name(), spec.sample(rng)));
                Duration budget = activity.budget(new ParamValues(sampled));
                assertTrue(budget.compareTo(Duration.ofMinutes(4)) >= 0 && budget.compareTo(Duration.ofMinutes(25)) <= 0,
                        activity.id() + " budget " + budget);
            }
        }
    }

    @Test
    void everyPaletteChoiceIsAValidPalette() {
        for (Activity activity : registry().all()) {
            activity.params().stream()
                    .filter(spec -> spec.name().equals("palette"))
                    .forEach(spec -> ((ParamSpec.Choice) spec).values().forEach(value ->
                            assertDoesNotThrow(() -> dev.vibrato.tasks.build.Palette.valueOf(value.toUpperCase(java.util.Locale.ROOT)))));
        }
    }
}
