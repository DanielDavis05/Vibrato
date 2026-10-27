package dev.vibrato.director;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldFactsTest {

    @Test
    void overworldSurfaceAndPickaxeTiers() {
        Set<Fact> facts = WorldFacts.compose("overworld", true, List.of("oak_planks", "iron_pickaxe", "stick"));

        assertTrue(facts.contains(Fact.dimension("overworld")));
        assertTrue(facts.contains(WorldFacts.SURFACE));
        assertTrue(facts.contains(Fact.has("iron_pickaxe")));
        assertFalse(facts.contains(WorldFacts.UNDERGROUND));
        assertEquals(3, facts.size());
    }

    @Test
    void undergroundWithoutPickaxe() {
        Set<Fact> facts = WorldFacts.compose("overworld", false, List.of("cobblestone"));

        assertTrue(facts.contains(WorldFacts.UNDERGROUND));
        assertFalse(WorldFacts.hasPickaxe(facts));
    }

    @Test
    void netherHasNoSurfaceFact() {
        Set<Fact> facts = WorldFacts.compose("nether", false, List.of());

        assertEquals(Set.of(Fact.dimension("nether")), facts);
    }

    @Test
    void observableFactsAreDimensionLocationAndPickaxes() {
        assertTrue(WorldFacts.isObservable(Fact.dimension("end")));
        assertTrue(WorldFacts.isObservable(WorldFacts.SURFACE));
        assertTrue(WorldFacts.isObservable(Fact.has("wooden_pickaxe")));
        assertFalse(WorldFacts.isObservable(Fact.has("bed")));
        assertFalse(WorldFacts.isObservable(Fact.of("spawn:set")));
    }
}
