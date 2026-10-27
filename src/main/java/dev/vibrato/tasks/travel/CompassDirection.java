package dev.vibrato.tasks.travel;

import net.minecraft.util.math.BlockPos;

/** The eight compass points, used to pick a travel bearing without spelling out coordinates. */
public enum CompassDirection {
    N(0, -1),
    NE(1, -1),
    E(1, 0),
    SE(1, 1),
    S(0, 1),
    SW(-1, 1),
    W(-1, 0),
    NW(-1, -1);

    private static final double DIAGONAL = Math.sqrt(0.5);

    private final double dx;
    private final double dz;

    CompassDirection(double dx, double dz) {
        this.dx = dx;
        this.dz = dz;
    }

    /** The point {@code distance} blocks from {@code from} in this direction, at the same height. */
    public BlockPos offset(BlockPos from, int distance) {
        double scale = (dx != 0 && dz != 0) ? DIAGONAL : 1.0;
        return new BlockPos(
                from.getX() + (int) Math.round(dx * scale * distance),
                from.getY(),
                from.getZ() + (int) Math.round(dz * scale * distance));
    }
}
