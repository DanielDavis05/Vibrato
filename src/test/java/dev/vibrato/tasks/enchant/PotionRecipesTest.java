package dev.vibrato.tasks.enchant;

import dev.vibrato.tasks.enchant.PotionRecipes.Potion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PotionRecipesTest {

    @Test
    void awkwardBaseIsNetherWart() {
        assertEquals(List.of("nether_wart"), Potion.AWKWARD.ingredients());
    }

    @Test
    void speedIsAwkwardThenSugar() {
        assertEquals(List.of("nether_wart", "sugar"), Potion.SPEED.ingredients());
    }

    @Test
    void extendedSwiftnessAddsRedstoneLast() {
        assertEquals(List.of("nether_wart", "sugar", "redstone"), Potion.SWIFTNESS_EXTENDED.ingredients());
    }

    @Test
    void everyChainStartsFromAwkwardBase() {
        for (Potion potion : Potion.values()) {
            assertEquals("nether_wart", potion.ingredients().get(0), potion.name());
        }
    }

    @Test
    void coversSevenPotionTypes() {
        assertEquals(7, Potion.values().length);
    }
}
