package dev.vibrato.tasks.build;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.Optional;

/** Scans the ground around the player and records the best flat footprint for a given size. */
public class FindBuildSiteTask extends Task {

    private static final int SCAN_RADIUS = 14;

    private final int _width;
    private final int _depth;
    private SiteScorer.Site _site;
    private boolean _searched = false;

    public FindBuildSiteTask(int width, int depth) {
        _width = width;
        _depth = depth;
    }

    /** The ground block to build on, if a site was found. */
    public Optional<BlockPos> site() {
        return Optional.ofNullable(_site).map(site -> new BlockPos(site.x(), site.groundY(), site.z()));
    }

    @Override
    protected void onStart(Vibrato mod) {
        _searched = false;
        _site = null;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        setDebugState("Looking for a flat site");
        _site = search(mod);
        _searched = true;
        return null;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _searched;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof FindBuildSiteTask task && task._width == _width && task._depth == _depth;
    }

    @Override
    protected String toDebugString() {
        return "Finding a " + _width + "x" + _depth + " build site";
    }

    private SiteScorer.Site search(Vibrato mod) {
        int size = 2 * SCAN_RADIUS + 1;
        if (_width > size || _depth > size) {
            return null;
        }
        BlockPos center = mod.getPlayer().getBlockPos();
        int minX = center.getX() - SCAN_RADIUS;
        int minZ = center.getZ() - SCAN_RADIUS;
        int[][] heights = new int[size][size];
        boolean[][] liquid = new boolean[size][size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int top = mod.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, minX + x, minZ + z);
                BlockPos ground = new BlockPos(minX + x, top - 1, minZ + z);
                heights[x][z] = ground.getY();
                // Unloaded chunks report the world floor, which would otherwise score as a perfectly flat site.
                boolean noGround = top <= mod.getWorld().getBottomY() + 1;
                liquid[x][z] = noGround || !mod.getWorld().getBlockState(ground).getFluidState().isEmpty();
            }
        }
        return SiteScorer.best(heights, liquid, _width, _depth)
                .map(site -> new SiteScorer.Site(minX + site.x(), minZ + site.z(), site.groundY(), site.score()))
                .orElse(null);
    }
}
