package dev.vibrato.tasks.build;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/** Scans the ground around the player and records the best flat footprint for a given size. */
public class FindBuildSiteTask extends Task {

    private static final int SCAN_RADIUS = 14;
    private static final int SCAN_UP = 8;
    private static final int SCAN_DOWN = 24;
    private static final int MAX_SEARCHES = 4;
    private static final float RELOCATE_DISTANCE = 24;

    private final int _width;
    private final int _depth;
    private SiteScorer.Site _site;
    private boolean _searched = false;
    private int _searches;
    private TimeoutWanderTask _relocate;

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
        _searches = 0;
        _relocate = null;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_relocate != null && _relocate.isActive() && !_relocate.isFinished(mod)) {
            setDebugState("Moving to look for a flat site");
            return _relocate;
        }
        setDebugState("Looking for a flat site");
        _site = search(mod);
        // Nearby chunks may not be loaded yet or the ground may be too uneven here: look elsewhere a few times.
        if (_site == null && ++_searches < MAX_SEARCHES) {
            _relocate = new TimeoutWanderTask(RELOCATE_DISTANCE);
            return _relocate;
        }
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

    /** Returns {groundY, 1 if that column is liquid or has no ground to stand on}. */
    private static int[] scanColumn(Vibrato mod, int x, int z, int playerY) {
        int top = Math.min((mod.getWorld().getTopY() - 1), playerY + SCAN_UP);
        int bottom = Math.max(mod.getWorld().getBottomY(), playerY - SCAN_DOWN);
        BlockPos.Mutable pos = new BlockPos.Mutable(x, top, z);
        for (int y = top; y >= bottom; y--) {
            pos.setY(y);
            BlockState state = mod.getWorld().getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                return new int[]{y, 1};
            }
            if (!state.getCollisionShape(mod.getWorld(), pos).isEmpty() && !state.isIn(BlockTags.LEAVES)) {
                return new int[]{y, 0};
            }
        }
        return new int[]{bottom, 1};
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
                // The client's NO_LEAVES heightmap is not populated for loaded chunks (it reads as the world
                // floor everywhere), so find the ground by scanning block states from just above the player.
                int[] column = scanColumn(mod, minX + x, minZ + z, center.getY());
                heights[x][z] = column[0];
                liquid[x][z] = column[1] != 0;
            }
        }
        return SiteScorer.best(heights, liquid, _width, _depth)
                .map(site -> new SiteScorer.Site(minX + site.x(), minZ + site.z(), site.groundY(), site.score()))
                .orElse(null);
    }
}
