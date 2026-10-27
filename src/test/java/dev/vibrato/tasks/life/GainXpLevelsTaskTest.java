package dev.vibrato.tasks.life;

import org.junit.jupiter.api.Test;
import static dev.vibrato.tasks.life.GainXpLevelsTask.Source.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GainXpLevelsTaskTest {
    @Test
    void keepsCurrentSourceWhenHigherPrioritySourceAppears() {
        assertEquals(MOBS, GainXpLevelsTask.SourcePolicy.choose(MOBS, false, true, true));
        assertEquals(ORE, GainXpLevelsTask.SourcePolicy.choose(ORE, true, true, true));
        assertEquals(SMELT, GainXpLevelsTask.SourcePolicy.choose(SMELT, true, true, true));
    }

    @Test
    void lostMiningPickIsReplacedBeforeChoosingAnotherSource() {
        assertTrue(GainXpLevelsTask.SourcePolicy.needsReplacement(ORE, false));
        assertFalse(GainXpLevelsTask.SourcePolicy.needsReplacement(ORE, true));
        assertFalse(GainXpLevelsTask.SourcePolicy.needsReplacement(SEARCH, false));
        assertFalse(GainXpLevelsTask.SourcePolicy.needsReplacement(SMELT, false));
    }

    @Test
    void switchesWhenSourceIsNoLongerUsable() {
        assertEquals(ORE, GainXpLevelsTask.SourcePolicy.choose(MOBS, false, true, false));
        assertEquals(MOBS, GainXpLevelsTask.SourcePolicy.choose(ORE, false, false, true));
        assertEquals(SEARCH, GainXpLevelsTask.SourcePolicy.choose(SMELT, false, false, false));
        assertEquals(SMELT, GainXpLevelsTask.SourcePolicy.choose(SEARCH, true, true, true));
    }
}
