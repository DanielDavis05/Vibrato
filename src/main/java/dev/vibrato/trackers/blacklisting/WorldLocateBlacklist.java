package dev.vibrato.trackers.blacklisting;

import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class WorldLocateBlacklist extends AbstractObjectBlacklist<BlockPos> {
    @Override
    protected Vec3d getPos(BlockPos item) {
        return WorldHelper.toVec3d(item);
    }
}
