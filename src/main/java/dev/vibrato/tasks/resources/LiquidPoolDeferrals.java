package dev.vibrato.tasks.resources;

import net.minecraft.util.math.BlockPos;
import java.util.HashMap;
import java.util.Map;

/** Temporary no-progress exclusions; unrelated or later approaches remain eligible. */
final class LiquidPoolDeferrals {
    private static final double RETRY_SECONDS = 90;
    private final Map<BlockPos, Double> pools = new HashMap<>();

    void defer(BlockPos anchor, double now) { pools.put(anchor.toImmutable(), now + RETRY_SECONDS); }
    boolean suppresses(BlockPos pos, double now) {
        pools.entrySet().removeIf(entry -> now >= entry.getValue());
        return pools.keySet().stream().anyMatch(anchor -> CollectBucketLiquidTask.samePool(anchor, pos));
    }
}
