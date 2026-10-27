package dev.vibrato.tasks.explore;

import java.util.List;
import java.util.Set;

/**
 * Structure ids the find-structure activities offer. Village has its own task ({@link FindVillageTask}) and is not listed.
 */
public final class ExplorableStructures {

    private static final List<String> IDS = List.of(
            "desert_temple", "jungle_temple", "shipwreck", "ruined_portal", "mineshaft", "stronghold");

    private static final Set<String> VALID = Set.copyOf(IDS);

    private ExplorableStructures() {}

    public static List<String> ids() {
        return IDS;
    }

    public static boolean isSupported(String id) {
        return VALID.contains(id);
    }
}
