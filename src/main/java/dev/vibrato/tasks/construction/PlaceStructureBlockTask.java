package dev.vibrato.tasks.construction;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

/**
 * Place any throwaway block at a position
 */
public class PlaceStructureBlockTask extends PlaceBlockTask {
    public PlaceStructureBlockTask(BlockPos target) {
        super(target, new Block[]{}, true, true);
    }

    public PlaceStructureBlockTask(BlockPos target, BlockPos castOrigin) {
        super(target, new Block[]{}, true, false, castOrigin);
    }

    @Override
    public boolean isChurnGuardEligible() {
        return true;
    }
}