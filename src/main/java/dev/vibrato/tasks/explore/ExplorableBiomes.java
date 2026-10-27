package dev.vibrato.tasks.explore;

import java.util.List;
import java.util.Set;

/** Biome ids the explore activities offer. Plain strings so the table can be tested without the game. */
public final class ExplorableBiomes {

    private static final List<String> IDS = List.of(
            "plains", "forest", "desert", "savanna", "jungle", "swamp", "taiga", "snowy_plains",
            "mushroom_fields", "badlands", "ocean", "dark_forest", "cherry_grove", "mangrove_swamp", "meadow");

    private static final Set<String> VALID = Set.copyOf(IDS);

    private ExplorableBiomes() {}

    public static List<String> ids() {
        return IDS;
    }

    public static boolean isSupported(String id) {
        return VALID.contains(id);
    }
}
