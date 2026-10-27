package dev.vibrato.tasks.explore;

import dev.vibrato.nav.Waypoint;
import dev.vibrato.nav.WaypointKind;
import dev.vibrato.nav.WaypointMemory;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/** Reads the spawn point the bot last slept at (recorded as a BED waypoint) for a given dimension. */
public final class SpawnPointLookup {

    private SpawnPointLookup() {}

    /** @return the recorded bed in {@code dimension}, or empty if the bot has none there */
    public static Optional<BlockPos> bedIn(WaypointMemory memory, String dimension) {
        return memory.get(WaypointKind.BED)
                .filter(waypoint -> waypoint.dimension().equals(dimension))
                .map(SpawnPointLookup::toPos);
    }

    private static BlockPos toPos(Waypoint waypoint) {
        return new BlockPos(waypoint.x(), waypoint.y(), waypoint.z());
    }
}
