package dev.vibrato.tasks.enchant;

import java.util.List;

/**
 * Brewing chains as catalogue item names. Every chain starts from water bottles, so the first ingredient is always
 * nether wart (awkward base), and each following ingredient is brewed into the potions already in the stand.
 */
public final class PotionRecipes {

    private PotionRecipes() {}

    public enum Potion {
        AWKWARD("nether_wart"),
        HEALING("nether_wart", "glistering_melon_slice"),
        SPEED("nether_wart", "sugar"),
        STRENGTH("nether_wart", "blaze_powder"),
        FIRE_RESISTANCE("nether_wart", "magma_cream"),
        NIGHT_VISION("nether_wart", "golden_carrot"),
        SWIFTNESS_EXTENDED("nether_wart", "sugar", "redstone");

        private final List<String> ingredients;

        Potion(String... ingredients) {
            this.ingredients = List.of(ingredients);
        }

        /** Ingredients in the order they are brewed, one brewing run each. */
        public List<String> ingredients() {
            return ingredients;
        }
    }
}
