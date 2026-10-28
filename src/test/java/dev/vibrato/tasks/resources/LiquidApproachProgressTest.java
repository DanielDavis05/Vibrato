package dev.vibrato.tasks.resources;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiquidApproachProgressTest {
    @Test void neighboringSourcesShareOneStableLeaseAndPillarBreaksCannotRenewForever() {
        var p = new LiquidApproachProgress<BlockPos>(45, .5);
        var a = new BlockPos(40, -55, 69);
        var b = new BlockPos(40, -55, 72);
        assertFalse(p.stalled(a, CollectBucketLiquidTask::samePool, anchor -> 50, 0, 0));
        assertFalse(p.stalled(b, CollectBucketLiquidTask::samePool, anchor -> 50, 1, 20));
        // First break gets one bounded excavation grace; later breaks don't renew it.
        for (int t = 21; t < 65; t++)
            assertFalse(p.stalled(t % 2 == 0 ? a : b, CollectBucketLiquidTask::samePool, anchor -> 50, t, t));
        assertTrue(p.stalled(b, CollectBucketLiquidTask::samePool, anchor -> 50, 65, 65));
        assertEquals(a, p.anchor());
        assertFalse(CollectBucketLiquidTask.samePool(a, new BlockPos(60, -55, 69)));
    }
    @Test void stalledPoolDeferralIsScopedAndExpires() {
        var deferred = new LiquidPoolDeferrals();
        var a = new BlockPos(40, -55, 69);
        var nearby = new BlockPos(40, -55, 72);
        var far = new BlockPos(60, -55, 69);
        deferred.defer(a, 100);
        assertTrue(deferred.suppresses(nearby, 101));
        assertFalse(deferred.suppresses(far, 101));
        assertFalse(deferred.suppresses(nearby, 190));
    }
    @Test void durableDescentRenewsLeaseAndRestoresOneExcavationGrace() {
        var p = new LiquidApproachProgress<String>(45, .5);
        assertFalse(p.stalled("pool", String::equals, a -> 100, 0, 0));
        assertFalse(p.stalled("pool", String::equals, a -> 100, 1, 30));
        assertFalse(p.stalled("pool", String::equals, a -> 99, 1, 60));
        assertFalse(p.stalled("pool", String::equals, a -> 99, 2, 100));
        assertTrue(p.stalled("pool", String::equals, a -> 99, 3, 145));
    }
    @Test void interruptionPausesTheLeaseWithoutErasingEffort() {
        var p = new LiquidApproachProgress<String>(45, .5);
        assertFalse(p.stalled("lava", String::equals, a -> 80, 0, 0));
        p.pause(30); p.resume(130);
        assertFalse(p.stalled("lava", String::equals, a -> 80, 0, 144));
        assertTrue(p.stalled("lava", String::equals, a -> 80, 0, 145));
        assertFalse(p.stalled("other", String::equals, a -> 80, 0, 146));
    }
    @Test void returningToOldBestIsNotNewProgress() {
        var p = new LiquidApproachProgress<String>(45, .5);
        assertFalse(p.stalled("lava", String::equals, a -> 80, 0, 0));
        assertFalse(p.stalled("lava", String::equals, a -> 85, 0, 20));
        assertFalse(p.stalled("lava", String::equals, a -> 80, 0, 40));
        assertTrue(p.stalled("lava", String::equals, a -> 80, 0, 45));
    }
}
