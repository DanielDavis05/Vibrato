package dev.vibrato.nav;

import dev.vibrato.Vibrato;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/** Surface and escape-target checks that do not mistake trees for cave ceilings. */
public final class SurfaceAccess {
    private SurfaceAccess() {}

    public static boolean isAtSurface(Vibrato mod) {
        BlockPos feet = mod.getPlayer().getBlockPos();
        if (mod.getWorld().isSkyVisible(feet)) return true;
        int top = mod.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, feet.getX(), feet.getZ());
        return SurfaceColumn.exposedToSurface(feet.getY(), top, y -> {
            BlockState state = mod.getWorld().getBlockState(new BlockPos(feet.getX(), y, feet.getZ()));
            return state.isOpaque() && !isCover(state);
        });
    }

    public static BlockPos target(Vibrato mod) {
        BlockPos feet = mod.getPlayer().getBlockPos();
        int top = mod.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, feet.getX(), feet.getZ());
        int y = SurfaceColumn.targetY(feet.getY(), top, atY -> {
            BlockState state = mod.getWorld().getBlockState(new BlockPos(feet.getX(), atY, feet.getZ()));
            return state.isAir() || isCover(state);
        });
        return new BlockPos(feet.getX(), y, feet.getZ());
    }

    private static boolean isCover(BlockState state) {
        return state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS)
                || state.isIn(BlockTags.PLANKS) || state.isIn(BlockTags.WOODEN_SLABS)
                || state.isIn(BlockTags.WOODEN_STAIRS);
    }
}
