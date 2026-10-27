package dev.vibrato.human.motion;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class HumanProfileTest {

    @Test
    void sameSeedGivesSameProfile() {
        HumanProfile first = HumanProfile.sample(new SplittableRandom(7), Population.DEFAULT);
        HumanProfile second = HumanProfile.sample(new SplittableRandom(7), Population.DEFAULT);
        assertEquals(first, second);
    }

    @Test
    void differentSeedsGiveDifferentProfiles() {
        assertNotEquals(
                HumanProfile.sample(new SplittableRandom(1), Population.DEFAULT),
                HumanProfile.sample(new SplittableRandom(2), Population.DEFAULT));
    }

    @Test
    void sampledTraitsStayInsideTheirRanges() {
        for (long seed = 0; seed < 200; seed++) {
            HumanProfile p = HumanProfile.sample(new SplittableRandom(seed), Population.DEFAULT);
            assertTrue(p.reactionMedianTicks() >= 1 && p.reactionMedianTicks() <= 20);
            assertTrue(p.jumpMissChance() >= 0 && p.jumpMissChance() <= 0.5);
            assertTrue(p.hesitationRate() >= 0 && p.hesitationRate() <= 0.05);
            assertTrue(p.lateralReversion() >= 0.001 && p.lateralReversion() <= 1);
            assertTrue(p.primaryFraction() >= 0.85 && p.primaryFraction() <= 0.99);
            assertTrue(p.correctionCountMean() >= 0 && p.correctionCountMean() <= 3);
            assertTrue(p.turnPeakSpeedScale() >= 0.5 && p.turnPeakSpeedScale() <= 1.5);
        }
    }

    @Test
    void typicalProfileIsThePopulationMeans() {
        HumanProfile typical = HumanProfile.typical(Population.DEFAULT);
        assertEquals(5, typical.reactionMedianTicks());
        assertEquals(0.92, typical.primaryFraction());
        assertEquals(1.2, typical.correctionCountMean());
    }
}
