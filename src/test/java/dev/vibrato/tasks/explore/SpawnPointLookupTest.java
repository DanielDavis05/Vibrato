package dev.vibrato.tasks.explore;

import dev.vibrato.nav.Waypoint;
import dev.vibrato.nav.WaypointKind;
import dev.vibrato.nav.WaypointMemory;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnPointLookupTest {

    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void findsRecordedBedInItsDimension() {
        WaypointMemory memory = new WaypointMemory();
        memory.set(WaypointKind.BED, new Waypoint(WaypointKind.BED, OVERWORLD, 10, 64, -20));
        assertEquals(new BlockPos(10, 64, -20), SpawnPointLookup.bedIn(memory, OVERWORLD).orElseThrow());
    }

    @Test
    void bedInAnotherDimensionIsNotTheSpawnHere() {
        WaypointMemory memory = new WaypointMemory();
        memory.set(WaypointKind.BED, new Waypoint(WaypointKind.BED, OVERWORLD, 10, 64, -20));
        assertTrue(SpawnPointLookup.bedIn(memory, "minecraft:the_nether").isEmpty());
    }

    @Test
    void noBedRecordedGivesNothing() {
        assertTrue(SpawnPointLookup.bedIn(new WaypointMemory(), OVERWORLD).isEmpty());
    }
}
