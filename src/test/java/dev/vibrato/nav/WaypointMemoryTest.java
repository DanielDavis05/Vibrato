package dev.vibrato.nav;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointMemoryTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @TempDir
    Path dir;

    @Test
    void setAndGetByName() {
        WaypointMemory memory = new WaypointMemory();
        Waypoint home = new Waypoint(WaypointKind.HOME, OVERWORLD, 10, 64, -20);
        memory.set("HOME", home);
        assertEquals(Optional.of(home), memory.get("HOME"));
        assertFalse(memory.get("BED").isPresent());
    }

    @Test
    void nearestPicksClosestOfKindInDimension() {
        WaypointMemory memory = new WaypointMemory();
        memory.set("CHEST#1", new Waypoint(WaypointKind.CHEST, OVERWORLD, 0, 64, 0));
        memory.set("CHEST#2", new Waypoint(WaypointKind.CHEST, OVERWORLD, 100, 64, 100));
        memory.set("HOME", new Waypoint(WaypointKind.HOME, OVERWORLD, 5, 64, 5));
        memory.set("CHEST#NETHER", new Waypoint(WaypointKind.CHEST, NETHER, 2, 64, 2));

        Optional<Waypoint> nearest = memory.nearest(WaypointKind.CHEST, OVERWORLD, 3, 64, 3);
        assertEquals(Optional.of(new Waypoint(WaypointKind.CHEST, OVERWORLD, 0, 64, 0)), nearest);
    }

    @Test
    void nearestIsEmptyWhenNothingMatches() {
        WaypointMemory memory = new WaypointMemory();
        memory.set("DEATH", new Waypoint(WaypointKind.DEATH, NETHER, 0, 0, 0));
        assertFalse(memory.nearest(WaypointKind.DEATH, OVERWORLD, 0, 0, 0).isPresent());
        assertFalse(memory.nearest(WaypointKind.BED, NETHER, 0, 0, 0).isPresent());
    }

    @Test
    void customKindsWork() {
        WaypointMemory memory = new WaypointMemory();
        memory.set("farm", new Waypoint("FARM", OVERWORLD, 1, 2, 3));
        assertTrue(memory.nearest("FARM", OVERWORLD, 0, 0, 0).isPresent());
    }

    @Test
    void saveAndLoadRoundTrip() throws IOException {
        Path file = dir.resolve("nested").resolve("waypoints.json");
        WaypointMemory memory = new WaypointMemory();
        memory.set("DEATH", new Waypoint(WaypointKind.DEATH, NETHER, -12, 40, 301));
        memory.set("HOME", new Waypoint(WaypointKind.HOME, OVERWORLD, 10, 64, -20));
        memory.save(file);

        WaypointMemory loaded = WaypointMemory.load(file);
        assertEquals(Optional.of(new Waypoint(WaypointKind.DEATH, NETHER, -12, 40, 301)), loaded.get("DEATH"));
        assertEquals(Optional.of(new Waypoint(WaypointKind.HOME, OVERWORLD, 10, 64, -20)), loaded.get("HOME"));
    }

    @Test
    void missingFileGivesEmptyMemory() {
        WaypointMemory loaded = WaypointMemory.load(dir.resolve("absent.json"));
        assertFalse(loaded.get("HOME").isPresent());
    }

    @Test
    void corruptFileGivesEmptyMemory() throws IOException {
        Path file = dir.resolve("corrupt.json");
        Files.writeString(file, "{ this is not json");
        WaypointMemory loaded = WaypointMemory.load(file);
        assertFalse(loaded.nearest(WaypointKind.HOME, OVERWORLD, 0, 0, 0).isPresent());
    }

    @Test
    void emptyFileGivesEmptyMemory() throws IOException {
        Path file = dir.resolve("empty.json");
        Files.writeString(file, "");
        assertFalse(WaypointMemory.load(file).get("HOME").isPresent());
    }
}
