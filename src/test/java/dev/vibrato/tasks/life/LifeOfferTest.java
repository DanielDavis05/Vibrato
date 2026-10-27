package dev.vibrato.tasks.life;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every choice an activity offers must be accepted by the task it builds. The activities read their choices from
 * these same tables, so these checks catch a value offered without support.
 */
class LifeOfferTest {

    @Test
    void everyOfferedRawFoodCanBeCooked() {
        assertFalse(CookableFoods.rawIds().isEmpty());
        for (String raw : CookableFoods.rawIds()) {
            assertTrue(CookableFoods.cookedIdFor(raw).isPresent(), raw);
        }
    }

    @Test
    void potatoBakesAndUnknownFoodDoesNotCook() {
        assertEquals("baked_potato", CookableFoods.cookedIdFor("potato").orElseThrow());
        assertTrue(CookableFoods.cookedIdFor("cake").isEmpty());
    }

    @Test
    void everyOfferedCropIsFarmable() {
        for (String crop : CropGrowth.itemIds()) {
            assertTrue(CropGrowth.forItemId(crop).isPresent(), crop);
        }
    }

    @Test
    void everyOfferedAnimalCanBeBredWithFood() {
        for (String animal : BreedingFood.animals()) {
            assertTrue(BreedingFood.canBreed(animal), animal);
            assertFalse(BreedingFood.foodsFor(animal).isEmpty(), animal);
        }
    }

    @Test
    void everyOfferedHarvestCropHasAGrowingBlock() {
        for (String crop : HarvestableCrops.itemIds()) {
            HarvestableCrops harvestable = HarvestableCrops.forItemId(crop).orElseThrow();
            assertFalse(harvestable.blockId().isBlank(), crop);
        }
    }
}
