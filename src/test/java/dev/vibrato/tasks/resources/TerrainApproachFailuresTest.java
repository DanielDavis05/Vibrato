package dev.vibrato.tasks.resources;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainApproachFailuresTest {
    private static final long SECOND = 1_000_000_000L;

    @Test void neighboringStoneRetargetsShareLeaseAndFailedClusterIsTemporary() {
        var watchdog = new MiningApproachWatchdog<BlockPos>();
        var failures = new TerrainApproachFailures();
        var first = new BlockPos(27, 48, 3);
        var adjacent = new BlockPos(28, 48, 3);
        var distant = new BlockPos(40, 48, 3);
        assertFalse(watchdog.stalled(first, 0, TerrainApproachFailures::sameArea, p -> 16));
        assertFalse(watchdog.stalled(adjacent, 21 * SECOND, TerrainApproachFailures::sameArea, p -> 16));
        assertTrue(watchdog.stalled(first, 45 * SECOND, TerrainApproachFailures::sameArea, p -> 16));
        failures.reject(watchdog.anchor(), 27, 64, 3, 45 * SECOND);
        assertTrue(failures.suppress(adjacent, 27, 64, 3, 46 * SECOND));
        assertFalse(failures.suppress(distant, 27, 64, 3, 46 * SECOND));
        assertFalse(failures.suppress(first, 27, 64, 3, 135 * SECOND));
    }

    @Test void meaningfulPlayerMovementOrActualExcavationAllowsRetry() {
        var failures = new TerrainApproachFailures();
        var target = new BlockPos(27, 48, 3);
        failures.reject(target, 27, 64, 3, 0);
        assertFalse(failures.suppress(target, 31, 64, 3, SECOND));
        failures.reject(target, 27, 64, 3, SECOND);
        failures.clear(); // called only after an excavated block is observed gone
        assertFalse(failures.suppress(target, 27, 64, 3, 2 * SECOND));
    }
}
