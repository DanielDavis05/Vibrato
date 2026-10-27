package dev.vibrato.director;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonasTest {

    @Test
    void presetNamesAreUniqueAndIncludeGeneralist() {
        List<Persona> all = Personas.all();
        Set<String> names = new HashSet<>();
        all.forEach(persona -> names.add(persona.name()));

        assertEquals(all.size(), names.size());
        assertTrue(names.contains("generalist"));
    }

    @Test
    void specialistsBoostAtLeastTwoAreasAndWeightsStayInRange() {
        for (Persona persona : Personas.all()) {
            for (double weight : persona.bias().values()) {
                assertTrue(weight >= 0.4 && weight <= 3.0, persona.name() + " weight " + weight);
            }
            if (persona != Personas.GENERALIST) {
                long boosted = persona.bias().values().stream().filter(weight -> weight > 1.0).count();
                assertTrue(boosted >= 2, persona.name() + " boosts " + boosted + " areas");
            }
        }
    }

    @Test
    void byNameIsCaseInsensitiveAndNullForUnknown() {
        assertSame(Personas.MINER, Personas.byName("MINER"));
        assertSame(Personas.TRADER, Personas.byName("Trader"));
        assertNull(Personas.byName("wizard"));
        assertNull(Personas.byName(null));
    }

    @Test
    void forSeedIsDeterministicAndCoversEveryPersona() {
        assertSame(Personas.forSeed(5), Personas.forSeed(5));

        Set<String> seen = new HashSet<>();
        for (long seed = 0; seed < 400; seed++) {
            seen.add(Personas.forSeed(seed).name());
        }
        assertEquals(Personas.all().size(), seen.size());
    }
}
