package dev.vibrato.tasks.travel;

import java.util.List;
import java.util.Set;

/**
 * The animals a bot can ride, as plain ids so the activity table and its tests do not touch the game.
 * {@link RideAnimalTask} maps each id to its entity class; {@link #isSupported} is the single source of truth.
 */
public final class RideableAnimals {

    /** Horses and donkeys are tamed by repeated mounting; pigs only carry a rider when saddled. */
    private static final Set<String> IDS = Set.of("horse", "donkey", "pig");
    private static final Set<String> NEEDS_SADDLE = Set.of("pig");

    private RideableAnimals() {}

    public static List<String> ids() {
        return IDS.stream().sorted().toList();
    }

    public static boolean isSupported(String id) {
        return IDS.contains(id);
    }

    public static boolean needsSaddle(String id) {
        return NEEDS_SADDLE.contains(id);
    }
}
