package dev.vibrato.tasks.construction;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Predicate;

/** Conservative bounded local walking route: no breaking, one-block rises and drops. */
final class PlacementRoute {
    private PlacementRoute() {}
    enum SiteAction { SEARCH, APPROACH, WAIT, FAIL }
    static SiteAction siteAction(boolean far, boolean loaded, boolean timedOut) {
        if (!far && loaded) return SiteAction.SEARCH;
        if (timedOut) return SiteAction.FAIL;
        return far ? SiteAction.APPROACH : SiteAction.WAIT;
    }
    enum GroundAction { PROCEED, WAIT, FAIL }
    static GroundAction groundAction(boolean grounded, boolean waitStarted, boolean timedOut) {
        return grounded ? GroundAction.PROCEED : waitStarted && timedOut ? GroundAction.FAIL : GroundAction.WAIT;
    }

    static boolean reachable(BlockPos start, BlockPos goal, Predicate<BlockPos> clear,
                             Predicate<BlockPos> solid) {
        return find(start, goal, clear, solid).isPresent();
    }

    static Optional<List<BlockPos>> find(BlockPos start, BlockPos goal, Predicate<BlockPos> clear,
                                         Predicate<BlockPos> solid) {
        if (!solid.test(start.down()) || !clear.test(start) || !clear.test(start.up())) return Optional.empty();
        if (Math.abs(start.getX() - goal.getX()) > 12 || Math.abs(start.getZ() - goal.getZ()) > 12
                || Math.abs(start.getY() - goal.getY()) > 6) return Optional.empty();
        int minX = Math.min(start.getX(), goal.getX()) - 3, maxX = Math.max(start.getX(), goal.getX()) + 3;
        int minZ = Math.min(start.getZ(), goal.getZ()) - 3, maxZ = Math.max(start.getZ(), goal.getZ()) + 3;
        int minY = Math.min(start.getY(), goal.getY()) - 2, maxY = Math.max(start.getY(), goal.getY()) + 2;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        queue.add(start); seen.add(start);
        int[][] directions = {{1,0},{-1,0},{0,1},{0,-1}};
        while (!queue.isEmpty() && seen.size() <= 2048) {
            BlockPos here = queue.remove();
            if (here.equals(goal)) {
                List<BlockPos> path = new ArrayList<>();
                for (BlockPos p = here; p != null; p = previous.get(p)) path.add(p);
                Collections.reverse(path);
                return Optional.of(List.copyOf(path));
            }
            for (int[] dir : directions) for (int dy = -1; dy <= 1; dy++) {
                BlockPos next = here.add(dir[0], dy, dir[1]);
                if (next.getX() < minX || next.getX() > maxX || next.getZ() < minZ || next.getZ() > maxZ
                        || next.getY() < minY || next.getY() > maxY || seen.contains(next)
                        || !solid.test(next.down()) || !clear.test(next) || !clear.test(next.up())
                        || (dy > 0 && !clear.test(here.up(2)))) continue;
                seen.add(next); previous.put(next, here); queue.add(next);
            }
        }
        return Optional.empty();
    }
}
