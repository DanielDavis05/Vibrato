package dev.vibrato.tasks.life;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Raw foods the bot can smelt, and what each one becomes. Ids are item ids without namespace. Pure data, so the
 * activity's offered raw foods and the task's table can be checked against each other in unit tests.
 */
public final class CookableFoods {

    private static final Map<String, String> COOKED = Map.ofEntries(
            Map.entry("beef", "cooked_beef"),
            Map.entry("porkchop", "cooked_porkchop"),
            Map.entry("mutton", "cooked_mutton"),
            Map.entry("chicken", "cooked_chicken"),
            Map.entry("rabbit", "cooked_rabbit"),
            Map.entry("cod", "cooked_cod"),
            Map.entry("salmon", "cooked_salmon"),
            Map.entry("potato", "baked_potato"),
            Map.entry("kelp", "dried_kelp")
    );

    private CookableFoods() {}

    public static List<String> rawIds() {
        return List.copyOf(COOKED.keySet().stream().sorted().toList());
    }

    public static Optional<String> cookedIdFor(String rawId) {
        return Optional.ofNullable(COOKED.get(rawId));
    }
}
