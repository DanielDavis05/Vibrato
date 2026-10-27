package dev.vibrato.human.motion;

import dev.vibrato.human.motion.Population.Trait;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class PopulationTest {

    @Test
    void drawsStayInsideEachTraitRange() {
        SplittableRandom rng = new SplittableRandom(3);
        for (Trait trait : Trait.values()) {
            for (int i = 0; i < 1000; i++) {
                double value = Population.DEFAULT.draw(trait, rng);
                assertTrue(value >= trait.min() && value <= trait.max(), trait + " " + value);
            }
        }
    }

    @Test
    void sameSeedGivesSameDraws() {
        SplittableRandom a = new SplittableRandom(11);
        SplittableRandom b = new SplittableRandom(11);
        for (int i = 0; i < 50; i++) {
            assertEquals(Population.DEFAULT.draw(Trait.LATERAL_SIGMA, a), Population.DEFAULT.draw(Trait.LATERAL_SIGMA, b));
        }
    }

    @Test
    void parseOverridesOnlyTheTraitsItNames() {
        Population parsed = Population.parse("{\"traits\":{\"reactionMedianTicks\":{\"mean\":7}}}");
        assertEquals(7, parsed.mean(Trait.REACTION_MEDIAN_TICKS));
        assertEquals(Population.DEFAULT.spread(Trait.REACTION_MEDIAN_TICKS), parsed.spread(Trait.REACTION_MEDIAN_TICKS));
        for (Trait trait : Trait.values()) {
            if (trait != Trait.REACTION_MEDIAN_TICKS) {
                assertEquals(Population.DEFAULT.mean(trait), parsed.mean(trait), trait.toString());
            }
        }
    }

    @Test
    void unknownTraitNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Population.parse("{\"traits\":{\"reactionMedianTick\":{\"mean\":7}}}"));
    }

    @Test
    void outOfRangeMeanIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Population.parse("{\"traits\":{\"hesitationRate\":{\"mean\":2}}}"));
    }

    @Test
    void shippedResourceMatchesBakedInDefaults() {
        assertEquals(Population.DEFAULT, Population.load());
    }
}
