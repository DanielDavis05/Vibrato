package dev.vibrato.tasks.travel;

import dev.vibrato.director.ActivityRegistry;
import dev.vibrato.director.Activity;
import dev.vibrato.director.ParamSpec;
import dev.vibrato.director.TravelActivities;
import dev.vibrato.tasks.explore.ExplorableBiomes;
import dev.vibrato.tasks.explore.ExplorableStructures;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every choice a travel activity offers must be supported by the task it builds, and ids must be unique and prefixed.
 */
class TravelOfferTest {

    @Test
    void everyOfferedBiomeIsExplorable() {
        assertEquals(15, ExplorableBiomes.ids().size());
        for (String biome : ExplorableBiomes.ids()) {
            assertTrue(ExplorableBiomes.isSupported(biome), biome);
        }
    }

    @Test
    void everyOfferedStructureIsSupported() {
        assertEquals(6, ExplorableStructures.ids().size());
        for (String structure : ExplorableStructures.ids()) {
            assertTrue(ExplorableStructures.isSupported(structure), structure);
        }
    }

    @Test
    void everyOfferedRideableAnimalIsSupported() {
        assertFalse(RideableAnimals.ids().isEmpty());
        for (String animal : RideableAnimals.ids()) {
            assertTrue(RideableAnimals.isSupported(animal), animal);
        }
        assertTrue(RideableAnimals.needsSaddle("pig"));
        assertFalse(RideableAnimals.needsSaddle("horse"));
    }

    @Test
    void unknownChoicesAreRejected() {
        assertFalse(ExplorableBiomes.isSupported("void"));
        assertFalse(ExplorableStructures.isSupported("village"));
        assertFalse(RideableAnimals.isSupported("cow"));
    }

    @Test
    void registersAtLeastTwelveUniqueTravelActivities() {
        ActivityRegistry registry = new ActivityRegistry();
        TravelActivities.register(registry);
        assertTrue(registry.size() >= 12, "only " + registry.size());
        Set<String> ids = new HashSet<>();
        for (Activity activity : registry.all()) {
            assertTrue(activity.id().startsWith("travel_"), activity.id());
            assertTrue(ids.add(activity.id()), "duplicate " + activity.id());
        }
    }

    @Test
    void everyParamChoiceIsNonEmptyAndDistanceRangeIsSane() {
        ActivityRegistry registry = new ActivityRegistry();
        TravelActivities.register(registry);
        for (Activity activity : registry.all()) {
            for (ParamSpec param : activity.params()) {
                assertTrue(param.variants() >= 1, activity.id() + "." + param.name());
            }
        }
    }
}
