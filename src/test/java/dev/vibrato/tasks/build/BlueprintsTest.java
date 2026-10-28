package dev.vibrato.tasks.build;

import dev.vibrato.tasks.build.Blueprint.Placement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.vibrato.tasks.build.BlockKind.*;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintsTest {
    @Test void executorSortsBeforePlacementAndRequestsFullInventoryTarget() {
        Blueprint blueprint = new Blueprint("column", java.util.List.of(
                new Blueprint.Placement(0, 4, 0, BlockKind.WALL),
                new Blueprint.Placement(1, 1, 0, BlockKind.WALL),
                new Blueprint.Placement(0, 1, 0, BlockKind.WALL)));
        var ordered = BuildBlueprintTask.executionOrder(blueprint).placements();
        assertEquals(1, ordered.get(0).dy());
        assertEquals(1, ordered.get(1).dy());
        assertEquals(4, ordered.get(2).dy());
        assertEquals(16, BuildBlueprintTask.desiredMaterialCount(16, 14));
    }


    @Test void protectsOnlyCompletedBlocksAtPlannedPositions() {
        var pos = new net.minecraft.util.math.BlockPos(1, 2, 3);
        var expected = java.util.Map.of(pos, "oak");
        assertTrue(BuildBlueprintTask.protectsCompleted(expected, pos, "oak"));
        assertFalse(BuildBlueprintTask.protectsCompleted(expected, pos, "stone"));
        assertFalse(BuildBlueprintTask.protectsCompleted(expected, pos.east(), "oak"));
    }

    private static long count(Blueprint blueprint, BlockKind kind) {
        return blueprint.placements().stream().filter(p -> p.kind() == kind).count();
    }

    private static void assertGrounded(Blueprint blueprint) {
        assertTrue(blueprint.floating().isEmpty(), () -> blueprint.name() + " has floating blocks: " + blueprint.floating());
        assertFalse(blueprint.isEmpty());
    }

    @Test
    void hutHasExpectedWallsFloorAndRoof() {
        Blueprint hut = Blueprints.hut(4, 4, 3);
        assertGrounded(hut);
        // ring of 12 cells, 3 storeys, minus 2 door cells and 3 windows; interior is 4 floor cells
        assertEquals(31, count(hut, WALL));
        assertEquals(4, count(hut, FLOOR));
        assertEquals(16, count(hut, ROOF));
        assertEquals(3, count(hut, WINDOW));
        assertEquals(1, count(hut, DOOR));
        assertEquals(1, count(hut, TORCH));
    }

    @Test
    void hutBoundingBoxMatchesFootprint() {
        Blueprint.Bounds bounds = Blueprints.hut(5, 6, 4).bounds();
        assertEquals(5, bounds.sizeX());
        assertEquals(6, bounds.sizeZ());
        assertEquals(1, bounds.minY());
        assertEquals(5, bounds.maxY());
    }

    @Test
    void doorHasOpenApproachAndHeadroomInside() {
        for (Blueprint blueprint : List.of(Blueprints.hut(3, 3, 3), Blueprints.hut(6, 6, 5), Blueprints.house(4, 4, 1), Blueprints.house(6, 5, 2))) {
            int door = blueprint.bounds().sizeX() / 2;
            assertTrue(blueprint.at(door, 1, 0).orElseThrow().kind() == DOOR, blueprint.name());
            assertFalse(blueprint.contains(door, 2, 0), blueprint.name() + " upper door cell blocked");
            assertFalse(blueprint.contains(door, 1, -1), blueprint.name() + " outside approach blocked");
            assertFalse(blueprint.contains(door, 2, -1), blueprint.name() + " outside headroom blocked");
            assertTrue(blueprint.contains(door, 1, 1), blueprint.name() + " no interior floor behind door");
            assertFalse(blueprint.contains(door, 2, 1), blueprint.name() + " interior step blocked");
            assertFalse(blueprint.contains(door, 3, 1), blueprint.name() + " interior headroom blocked");
        }
    }

    @Test
    void houseWithTwoFloorsHasLadderThroughHoleAndNoFloatingBlocks() {
        Blueprint house = Blueprints.house(4, 4, 2);
        assertGrounded(house);
        assertEquals(7, count(house, LADDER));
        assertEquals(LADDER, house.at(1, 5, 1).orElseThrow().kind());
        assertEquals(16, count(house, ROOF));
    }

    @Test
    void singleFloorHouseHasNoLadder() {
        Blueprint house = Blueprints.house(5, 5, 1);
        assertGrounded(house);
        assertEquals(0, count(house, LADDER));
    }

    @Test
    void towerIsRoundWithLadderAndNoFloatingBlocks() {
        Blueprint tower = Blueprints.tower(2, 10);
        assertGrounded(tower);
        assertEquals(9, count(tower, LADDER));
        assertTrue(count(tower, FLOOR) > 0);
        assertEquals(1, count(tower, DOOR));
        assertTrue(count(tower, WALL) > count(tower, ROOF) / 2);
    }

    @Test
    void towerRingIsCircular() {
        // radius 3 outer disk has 29 cells; its corners (3,3) are outside the disk
        Blueprint tower = Blueprints.tower(3, 6);
        assertFalse(tower.contains(3, 1, 3));
        assertFalse(tower.contains(3, 1, -3));
        assertTrue(tower.contains(3, 1, 0));
    }

    @Test
    void bridgeDeckAndRailings() {
        Blueprint bridge = Blueprints.bridge(10, 2);
        assertGrounded(bridge);
        assertEquals(20, count(bridge, FLOOR));
        assertEquals(20, count(bridge, FENCE));
        Blueprint narrow = Blueprints.bridge(8, 1);
        assertEquals(8, count(narrow, FENCE));
        assertEquals(8, count(narrow, FLOOR));
    }

    @Test
    void wallIsCrenellated() {
        Blueprint wall = Blueprints.wall(9, 3);
        assertGrounded(wall);
        assertEquals(27 + 5, count(wall, WALL));
    }

    @Test
    void farmHasChannelAndFarmland() {
        Blueprint farm = Blueprints.farm(4);
        assertEquals(4, count(farm, WATER));
        assertEquals(32, count(farm, FARMLAND));
        assertTrue(farm.materialCount(Palette.OAK).isEmpty());
        assertTrue(farm.bottomUp().get(0).dy() == 0);
    }

    @Test
    void pyramidShrinksEachLayer() {
        Blueprint pyramid = Blueprints.pyramid(5);
        assertGrounded(pyramid);
        assertEquals(25 + 9 + 1, pyramid.placements().size());
        assertEquals(3, pyramid.bounds().maxY());
    }

    @Test
    void obeliskIsPlinthAndPillar() {
        Blueprint obelisk = Blueprints.obelisk(6);
        assertGrounded(obelisk);
        assertEquals(9, count(obelisk, WALL));
        assertEquals(6, count(obelisk, PILLAR));
    }

    @Test
    void portalFrameIsFourteenObsidianAroundOpenInterior() {
        Blueprint portal = Blueprints.portalFrame();
        assertEquals(14, count(portal, OBSIDIAN));
        assertFalse(portal.contains(1, 2, 0));
        assertFalse(portal.contains(2, 4, 0));
        assertGrounded(portal);
    }

    @Test
    void penHasGateAndPerimeter() {
        Blueprint pen = Blueprints.pen(6, 5);
        assertGrounded(pen);
        assertEquals(1, count(pen, GATE));
        assertEquals(2 * 6 + 2 * 5 - 4 - 1, count(pen, FENCE));
        assertEquals(GATE, pen.at(3, 1, 0).orElseThrow().kind());
    }

    @Test
    void pathLineIsGroundLayer() {
        Blueprint path = Blueprints.pathLine(7);
        assertEquals(7, count(path, PATH));
        assertTrue(path.placements().stream().allMatch(p -> p.dy() == 0));
    }

    @Test
    void materialCountSumsPaletteItems() {
        Blueprint hut = Blueprints.hut(4, 4, 3);
        var counts = hut.materialCount(Palette.OAK);
        assertEquals(31 + 4 + 16, counts.get("oak_planks"));
        assertEquals(3, counts.get("glass_pane"));
        assertEquals(1, counts.get("oak_door"));
        assertEquals(1, counts.get("torch"));
        assertEquals(hut.placements().size(), counts.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void sizesOutOfRangeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Blueprints.hut(2, 4, 3));
        assertThrows(IllegalArgumentException.class, () -> Blueprints.tower(4, 10));
        assertThrows(IllegalArgumentException.class, () -> Blueprints.obelisk(4));
    }

    @Test
    void quarterTurnKeepsShape() {
        Blueprint bridge = Blueprints.bridge(8, 2);
        Blueprint turned = bridge.quarterTurned();
        assertEquals(bridge.placements().size(), turned.placements().size());
        assertEquals(bridge.bounds().sizeX(), turned.bounds().sizeZ());
        assertTrue(turned.floating().isEmpty());
    }

    @Test
    void duplicateCellsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Blueprint("bad",
                List.of(new Placement(0, 1, 0, WALL), new Placement(0, 1, 0, FLOOR))));
    }
}
