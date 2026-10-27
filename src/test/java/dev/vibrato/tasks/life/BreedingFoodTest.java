package dev.vibrato.tasks.life;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreedingFoodTest {

    @Test
    void cowsAndSheepEatWheat() {
        assertEquals(List.of("wheat"), BreedingFood.foodsFor("cow"));
        assertEquals(List.of("wheat"), BreedingFood.foodsFor("sheep"));
    }

    @Test
    void pigsAcceptRootVegetables() {
        assertEquals(List.of("carrot", "potato", "beetroot"), BreedingFood.foodsFor("pig"));
    }

    @Test
    void chickensEatSeeds() {
        assertTrue(BreedingFood.foodsFor("chicken").contains("wheat_seeds"));
    }

    @Test
    void onlyFarmAnimalsCanBeBred() {
        assertTrue(BreedingFood.canBreed("cow"));
        assertFalse(BreedingFood.canBreed("horse"));
        assertThrows(IllegalArgumentException.class, () -> BreedingFood.foodsFor("horse"));
    }

    @Test
    void picksMostPreferredOwnedFood() {
        assertEquals("carrot", BreedingFood.firstOwned("pig", Set.of("potato", "carrot")).orElseThrow());
        assertTrue(BreedingFood.firstOwned("pig", Set.of("wheat")).isEmpty());
        assertTrue(BreedingFood.firstOwned("cow", Set.of()).isEmpty());
    }
}
