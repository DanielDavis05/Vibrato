package dev.vibrato.tasks.travel;

import net.minecraft.util.math.BlockPos;

import java.util.function.Predicate;

/**
 * Decides how to cross water on the way to a target: swim short gaps, take a boat for long ones, bridge when there is no
 * boat but enough blocks, and otherwise let pathing find its own way round.
 * <p>
 * Pure on purpose: the caller counts the water and the materials, this class only decides.
 */
public final class WaterCrossingPlanner {

    /** Gaps up to this many water blocks are simply swum. */
    public static final int SWIM_LIMIT = 6;

    /** One stack of building blocks, the minimum worth bridging with. */
    public static final int BRIDGE_BLOCK_MINIMUM = 64;

    private WaterCrossingPlanner() {}

    public enum Crossing {
        SWIM,
        BOAT,
        BRIDGE,
        /** Long water, no boat and no blocks: path around it and accept whatever swimming pathing needs. */
        SWIM_AROUND
    }

    public static Crossing plan(int waterBlocksInLine, boolean hasBoat, int buildingBlocks) {
        if (waterBlocksInLine <= SWIM_LIMIT) {
            return Crossing.SWIM;
        }
        if (hasBoat) {
            return Crossing.BOAT;
        }
        if (buildingBlocks >= BRIDGE_BLOCK_MINIMUM) {
            return Crossing.BRIDGE;
        }
        return Crossing.SWIM_AROUND;
    }

    /**
     * Counts the water blocks on a straight horizontal line from {@code from} to {@code to}, sampled once per block
     * along the longer axis. The start and end blocks are not counted, since the bot stands on one and aims at the other.
     */
    public static int countWaterOnLine(BlockPos from, BlockPos to, Predicate<BlockPos> isWater) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        int count = 0;
        for (int i = 1; i < steps; i++) {
            int x = from.getX() + (int) Math.round((double) dx * i / steps);
            int z = from.getZ() + (int) Math.round((double) dz * i / steps);
            if (isWater.test(new BlockPos(x, from.getY(), z))) {
                count++;
            }
        }
        return count;
    }
}
