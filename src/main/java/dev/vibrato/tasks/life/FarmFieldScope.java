package dev.vibrato.tasks.life;

import net.minecraft.util.math.BlockPos;

/** Stable field center, retained while the player leaves to gather supplies. */
final class FarmFieldScope {
    private BlockPos anchor;

    void establish(BlockPos pos) {
        if (anchor == null && pos != null) anchor = pos.toImmutable();
    }
    BlockPos anchor() { return anchor; }

    boolean contains(BlockPos pos, BlockPos player) {
        BlockPos center = anchor == null ? player : anchor;
        return center.getSquaredDistance(pos.getX(), pos.getY(), pos.getZ()) <= 12 * 12;
    }
}
