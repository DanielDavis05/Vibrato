package dev.vibrato.tasks.resources;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiquidApproachProgressTest {
    @Test void crampedVerticalJumpingDoesNotKeepDeepSourceApproachAlive() {
        var progress = new LiquidApproachProgress<String>(45, .5);
        boolean stalled = false;
        for (int second = 0; second <= 50; second++) {
            // Production oscillated at Y61..64 while its source remained near Y-28.
            stalled = progress.stalled("deep-lava", 89 + (second % 3), 151, second);
        }
        assertTrue(stalled);
    }
    @Test void realDescentAndTerrainClearingKeepSlowApproachesAlive() {
        var descending = new LiquidApproachProgress<String>(45, .5);
        var mining = new LiquidApproachProgress<String>(45, .5);
        for (int second = 0; second < 300; second++) {
            assertFalse(descending.stalled("lava", 100 - second / 10.0, 0, second));
            assertFalse(mining.stalled("lava", 90, second / 20, second));
        }
    }
    @Test void aDetourHasGraceButCannotResetProgressByReturningToOldBest() {
        var progress = new LiquidApproachProgress<String>(45, .5);
        assertFalse(progress.stalled("lava", 80, 0, 0));
        assertFalse(progress.stalled("lava", 85, 0, 20));
        assertFalse(progress.stalled("lava", 80, 0, 40));
        assertTrue(progress.stalled("lava", 80, 0, 45));
        assertFalse(progress.stalled("different-source", 100, 0, 46));
        progress.reset();
        assertFalse(progress.stalled("different-source", 100, 0, 100));
    }
}
