package dev.vibrato.util.progresscheck;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MovementWindowProgressCheckerTest {
    private boolean elapsed;
    private final MovementWindowProgressChecker checker = new MovementWindowProgressChecker(
            0.1, () -> elapsed, () -> elapsed = false);

    @Test
    void returnAlongRouteIsProgress() {
        checker.setProgress(new Vec3d(0, 64, 0));
        elapsed = true;
        checker.setProgress(new Vec3d(5, 64, 0));
        elapsed = true;
        checker.setProgress(new Vec3d(1, 64, 0));
        assertFalse(checker.failed());
    }

    @Test
    void stationaryWindowFailsAndResetAllowsRetry() {
        checker.setProgress(new Vec3d(0, 64, 0));
        elapsed = true;
        checker.setProgress(new Vec3d(0.01, 64, 0));
        assertTrue(checker.failed());
        checker.reset();
        assertFalse(checker.failed());
    }

    @Test
    void verticalMovementCounts() {
        checker.setProgress(new Vec3d(0, 64, 0));
        elapsed = true;
        checker.setProgress(new Vec3d(0, 65, 0));
        assertFalse(checker.failed());
    }
}
