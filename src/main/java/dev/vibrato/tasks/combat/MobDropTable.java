package dev.vibrato.tasks.combat;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Which mob drops each item, by item id path. Pure: works on id strings so it is testable without registries. */
public final class MobDropTable {

    private static final Map<String, String> DROP_TO_MOB = Map.ofEntries(
            Map.entry("bone", "skeleton"),
            Map.entry("arrow", "skeleton"),
            Map.entry("string", "spider"),
            Map.entry("spider_eye", "spider"),
            Map.entry("gunpowder", "creeper"),
            Map.entry("ender_pearl", "enderman"),
            Map.entry("slime_ball", "slime"),
            Map.entry("leather", "cow"),
            Map.entry("beef", "cow"),
            Map.entry("feather", "chicken"),
            Map.entry("chicken", "chicken"),
            Map.entry("porkchop", "pig"),
            Map.entry("mutton", "sheep"),
            Map.entry("rabbit", "rabbit"),
            Map.entry("ink_sac", "squid"),
            Map.entry("blaze_rod", "blaze"),
            Map.entry("rotten_flesh", "zombie"));

    private MobDropTable() {}

    /** The mob id that drops this item, or empty when the item has no farmable source (for example ghast tears). */
    public static Optional<String> mobFor(String itemPath) {
        return Optional.ofNullable(DROP_TO_MOB.get(itemPath));
    }

    public static Set<String> supportedDrops() {
        return DROP_TO_MOB.keySet();
    }
}
