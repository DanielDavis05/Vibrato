package dev.vibrato.tasks.resources;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.movement.GetToBlockTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

/** Looks for unsearched loaded grassland before falling back to ordinary exploration. */
final class LandAnimalSearchTask extends Task {
    // This search reads client chunks. The no-leaves map is server-only and may be unprimed here.
    static final Heightmap.Type SURFACE_HEIGHTMAP = Heightmap.Type.MOTION_BLOCKING;
    private static final int RADIUS = 128;
    private static final int SCAN_INTERVAL = 20;
    private static final int[] SAMPLE_OFFSETS = {4, 12};
    private final LandScoutPolicy policy = new LandScoutPolicy();
    private final TimeoutWanderTask wander = new TimeoutWanderTask(9999999);
    private LandScoutPolicy.Candidate target;
    private Task travel;
    private int ticksUntilScan;

    @Override protected void onStart(Vibrato mod) {}

    @Override protected Task onTick(Vibrato mod) {
        BlockPos here = mod.getPlayer().getBlockPos();
        policy.visited(here.getX() >> 4, here.getZ() >> 4);
        boolean reached = target != null && travel != null && travel.isFinished(mod);
        if (reached) {
            policy.choose(here.getX(), here.getZ(), true, true, List.of());
            target = null;
            travel = null;
        }
        if (--ticksUntilScan <= 0) {
            ticksUntilScan = SCAN_INTERVAL;
            boolean valid = target != null && valid(mod, target);
            List<LandScoutPolicy.Candidate> candidates = target == null || !valid
                    ? scan(mod, here) : List.of();
            LandScoutPolicy.Candidate next = policy.choose(here.getX(), here.getZ(), valid, false, candidates);
            if (next != target) {
                target = next;
                travel = target == null ? null : new GetToBlockTask(position(target));
            }
        }
        if (target != null) {
            setDebugState("Searching grassland for animals");
            return travel;
        }
        setDebugState("Exploring for land animals");
        if (wander.isActive() && wander.isFinished(mod)) wander.resetWander();
        return wander;
    }

    private static List<LandScoutPolicy.Candidate> scan(Vibrato mod, BlockPos here) {
        List<LandScoutPolicy.Candidate> result = new ArrayList<>();
        for (ChunkPos chunk : mod.getChunkTracker().getLoadedChunks()) {
            if (Math.abs(chunk.getStartX() + 8 - here.getX()) > RADIUS
                    || Math.abs(chunk.getStartZ() + 8 - here.getZ()) > RADIUS) continue;
            for (int dx : SAMPLE_OFFSETS) for (int dz : SAMPLE_OFFSETS) {
                int x = chunk.getStartX() + dx, z = chunk.getStartZ() + dz;
                int y = mod.getWorld().getTopY(SURFACE_HEIGHTMAP, x, z);
                BlockPos feet = new BlockPos(x, y, z);
                if (mod.getWorld().getBlockState(feet.down()).isOf(Blocks.GRASS_BLOCK)) {
                    result.add(candidate(mod, feet));
                }
            }
        }
        return result;
    }

    private static boolean valid(Vibrato mod, LandScoutPolicy.Candidate target) {
        BlockPos pos = position(target);
        if (!mod.getChunkTracker().isChunkLoaded(pos)) return true;
        LandScoutPolicy.Candidate current = candidate(mod, pos);
        return current.grass() && current.clear() && current.dry() && current.reachable();
    }

    private static LandScoutPolicy.Candidate candidate(Vibrato mod, BlockPos feet) {
        var world = mod.getWorld();
        var ground = world.getBlockState(feet.down());
        var lower = world.getBlockState(feet);
        var upper = world.getBlockState(feet.up());
        boolean clear = lower.getCollisionShape(world, feet).isEmpty()
                && upper.getCollisionShape(world, feet.up()).isEmpty();
        boolean dry = lower.getFluidState().isEmpty() && upper.getFluidState().isEmpty();
        return new LandScoutPolicy.Candidate(feet.getX(), feet.getY(), feet.getZ(),
                ground.isOf(Blocks.GRASS_BLOCK), clear, dry, !mod.getBlockTracker().unreachable(feet));
    }

    private static BlockPos position(LandScoutPolicy.Candidate target) {
        return new BlockPos(target.x(), target.y(), target.z());
    }

    @Override protected void onStop(Vibrato mod, Task interruptTask) {}
    @Override protected boolean isEqual(Task other) { return this == other; }
    @Override protected String toDebugString() { return "Searching for land animals"; }
}
