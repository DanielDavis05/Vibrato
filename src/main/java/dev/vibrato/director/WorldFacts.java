package dev.vibrato.director;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Facts the bot can read off the world right now: dimension, surface or underground, and which pickaxe tiers it
 * carries. Pure: callers pass plain values.
 */
public final class WorldFacts {

    public static final Fact UNDERGROUND = Fact.of("loc:underground");
    public static final Fact SURFACE = Fact.of("loc:surface");

    private static final List<String> PICKAXES = List.of(
            "wooden_pickaxe", "stone_pickaxe", "iron_pickaxe", "diamond_pickaxe", "netherite_pickaxe");

    private WorldFacts() {}

    /**
     * @param dimension  {@code overworld}, {@code nether} or {@code end}
     * @param skyVisible whether the bot's block has open sky above it
     * @param heldItems  item ids in the inventory
     */
    public static Set<Fact> compose(String dimension, boolean skyVisible, Collection<String> heldItems) {
        Set<Fact> facts = new HashSet<>();
        facts.add(Fact.dimension(dimension));
        if ("overworld".equals(dimension)) {
            facts.add(skyVisible ? SURFACE : UNDERGROUND);
        }
        for (String item : heldItems) {
            if (PICKAXES.contains(item)) {
                facts.add(Fact.has(item));
            }
        }
        return facts;
    }

    /** Facts that are re-read from the world at every plan boundary; the rest persist across plans. */
    public static boolean isObservable(Fact fact) {
        String key = fact.key();
        return key.startsWith("dim:") || key.startsWith("loc:") || isPickaxeFact(fact);
    }

    public static boolean hasPickaxe(Set<Fact> facts) {
        return facts.stream().anyMatch(WorldFacts::isPickaxeFact);
    }

    private static boolean isPickaxeFact(Fact fact) {
        return fact.key().startsWith("has:") && PICKAXES.contains(fact.key().substring("has:".length()));
    }
}
