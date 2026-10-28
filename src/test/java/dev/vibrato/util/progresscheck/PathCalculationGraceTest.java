package dev.vibrato.util.progresscheck;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathCalculationGraceTest {
    private static final long SECOND = 1_000_000_000L;
    private final PathCalculationGrace grace = new PathCalculationGrace(15);

    @Test
    void stationaryCalculationIsExcusedOnlyForTheGraceWindow() {
        Vec3d spot = new Vec3d(0, 64, 0);
        assertTrue(grace.excuses(spot, 0));
        assertTrue(grace.excuses(spot, 14 * SECOND));
        assertFalse(grace.excuses(spot, 16 * SECOND));
    }

    @Test
    void movingPlayerRestartsTheWindow() {
        assertTrue(grace.excuses(new Vec3d(0, 64, 0), 0));
        assertTrue(grace.excuses(new Vec3d(5, 64, 0), 14 * SECOND));
        assertTrue(grace.excuses(new Vec3d(5, 64, 0), 28 * SECOND));
        assertFalse(grace.excuses(new Vec3d(5, 64, 0), 30 * SECOND));
    }

    @Test
    void resetAllowsAFreshWindow() {
        Vec3d spot = new Vec3d(0, 64, 0);
        grace.excuses(spot, 0);
        assertFalse(grace.excuses(spot, 20 * SECOND));
        grace.reset();
        assertTrue(grace.excuses(spot, 21 * SECOND));
    }
}
