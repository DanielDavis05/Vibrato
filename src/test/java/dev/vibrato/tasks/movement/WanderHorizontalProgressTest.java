package dev.vibrato.tasks.movement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WanderHorizontalProgressTest {
    @Test void verticalOscillationCannotKeepWanderAlive() {
        var progress = new WanderHorizontalProgress();
        progress.reset(39.5, 73.5, 0);
        assertFalse(progress.stalled(39.5, 73.5, 29));
        assertTrue(progress.stalled(39.5, 73.5, 30));
    }
    @Test void horizontalTravelRestartsTheWindow() {
        var progress = new WanderHorizontalProgress();
        progress.reset(0, 0, 0);
        assertFalse(progress.stalled(3, 0, 29));
        assertFalse(progress.stalled(3, 0, 58));
        assertTrue(progress.stalled(3, 0, 59));
    }
}
