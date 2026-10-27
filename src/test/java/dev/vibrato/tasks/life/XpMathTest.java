package dev.vibrato.tasks.life;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XpMathTest {

    @Test
    void totalXpMatchesVanillaCurve() {
        assertEquals(0, XpMath.totalXpForLevel(0));
        assertEquals(7, XpMath.totalXpForLevel(1));
        assertEquals(352, XpMath.totalXpForLevel(16));
        assertEquals(394, XpMath.totalXpForLevel(17));
        assertEquals(1395, XpMath.totalXpForLevel(30));
        assertEquals(1507, XpMath.totalXpForLevel(31));
        assertEquals(1628, XpMath.totalXpForLevel(32));
        assertEquals(5345, XpMath.totalXpForLevel(50));
    }

    @Test
    void perLevelCostChangesAtBreakpoints() {
        assertEquals(7, XpMath.xpToNextLevel(0));
        assertEquals(37, XpMath.xpToNextLevel(15));
        assertEquals(42, XpMath.xpToNextLevel(16));
        assertEquals(112, XpMath.xpToNextLevel(30));
        assertEquals(121, XpMath.xpToNextLevel(31));
    }

    @Test
    void levelForTotalXpIsTheInverse() {
        assertEquals(0, XpMath.levelForTotalXp(0));
        assertEquals(0, XpMath.levelForTotalXp(6));
        assertEquals(1, XpMath.levelForTotalXp(7));
        assertEquals(15, XpMath.levelForTotalXp(351));
        assertEquals(16, XpMath.levelForTotalXp(352));
        assertEquals(16, XpMath.levelForTotalXp(393));
        assertEquals(17, XpMath.levelForTotalXp(394));
        for (int level = 0; level <= XpMath.MAX_LEVEL; level++) {
            assertEquals(level, XpMath.levelForTotalXp(XpMath.totalXpForLevel(level)));
        }
    }

    @Test
    void levelForTotalXpCapsAtMaximum() {
        assertEquals(XpMath.MAX_LEVEL, XpMath.levelForTotalXp(1_000_000));
    }

    @Test
    void rejectsOutOfRangeInput() {
        assertThrows(IllegalArgumentException.class, () -> XpMath.totalXpForLevel(-1));
        assertThrows(IllegalArgumentException.class, () -> XpMath.totalXpForLevel(51));
        assertThrows(IllegalArgumentException.class, () -> XpMath.xpToNextLevel(50));
        assertThrows(IllegalArgumentException.class, () -> XpMath.levelForTotalXp(-5));
    }
}
