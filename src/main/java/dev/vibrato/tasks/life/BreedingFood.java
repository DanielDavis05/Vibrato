package dev.vibrato.tasks.life;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which items put a farm animal in love, in the order they should be preferred. Ids are item ids without namespace.
 */
public final class BreedingFood {

    private static final Map<String, List<String>> FOODS = Map.of(
            "cow", List.of("wheat"),
            "sheep", List.of("wheat"),
            "pig", List.of("carrot", "potato", "beetroot"),
            "chicken", List.of("wheat_seeds", "beetroot_seeds", "melon_seeds", "pumpkin_seeds")
    );

    private BreedingFood() {}

    /** Animals the breed activity offers, in a stable order. */
    public static List<String> animals() {
        return FOODS.keySet().stream().sorted().toList();
    }

    public static boolean canBreed(String animal) {
        return FOODS.containsKey(animal);
    }

    /** @throws IllegalArgumentException if the animal cannot be bred */
    public static List<String> foodsFor(String animal) {
        List<String> foods = FOODS.get(animal);
        if (foods == null) {
            throw new IllegalArgumentException("No breeding food known for " + animal);
        }
        return foods;
    }

    /** The most preferred breeding food the owner already has. */
    public static Optional<String> firstOwned(String animal, Set<String> owned) {
        return foodsFor(animal).stream().filter(owned::contains).findFirst();
    }
}
