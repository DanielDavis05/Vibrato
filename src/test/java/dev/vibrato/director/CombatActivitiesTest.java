package dev.vibrato.director;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatActivitiesTest {

    private static ActivityRegistry combatRegistry() {
        ActivityRegistry registry = new ActivityRegistry();
        CombatActivities.register(registry);
        return registry;
    }

    @Test
    void registersAtLeastTwelveUniqueActivities() {
        List<Activity> activities = combatRegistry().all();

        assertTrue(activities.size() >= 12, "only " + activities.size() + " activities");
        Set<String> ids = new HashSet<>();
        activities.forEach(activity -> ids.add(activity.id()));
        assertEquals(activities.size(), ids.size());
    }

    @Test
    void coversCombatGearAndGatherAreas() {
        Set<FeatureArea> areas = new HashSet<>();
        combatRegistry().all().forEach(activity -> areas.add(activity.area()));

        assertEquals(Set.of(FeatureArea.COMBAT, FeatureArea.GEAR, FeatureArea.GATHER), areas);
    }

    @Test
    void armorTiersProvideTheirFacts() {
        ActivityRegistry registry = combatRegistry();

        assertTrue(registry.get("upgrade_iron_armor").provides().contains(Fact.of("armor:iron")));
        assertTrue(registry.get("upgrade_diamond_armor").provides().contains(Fact.of("armor:diamond")));
        assertTrue(registry.get("upgrade_diamond_armor").provides().contains(Fact.of("tool:diamond")));
        assertTrue(registry.get("upgrade_netherite_armor").requires().contains(Fact.of("armor:diamond")));
    }

    @Test
    void blazeRodsNeedTheNether() {
        assertTrue(combatRegistry().get("blaze_rods").requires().contains(Fact.dimension("nether")));
    }

    @Test
    void everyActivityHasPositiveParamsAndBoundedIds() {
        for (Activity activity : combatRegistry().all()) {
            assertTrue(activity.id().matches("[a-z][a-z_]*"), activity.id());
            activity.params().forEach(param -> assertTrue(param.variants() >= 1, activity.id()));
        }
    }
}
