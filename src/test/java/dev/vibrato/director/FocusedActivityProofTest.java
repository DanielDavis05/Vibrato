package dev.vibrato.director;

import org.junit.jupiter.api.Test;
import java.util.*;
import dev.vibrato.packetcoverage.PacketCoverage;
import net.minecraft.screen.*;
import static org.junit.jupiter.api.Assertions.*;

class FocusedActivityProofTest {
    private static FocusedActivityProof.Inventory inventory(Map<String, Integer> counts, String... slots) {
        return new FocusedActivityProof.Inventory(counts, List.of(slots), Map.of(), Map.of());
    }
    private static Map<String, Long> transferPackets() {
        return Map.of("SERVERBOUND:minecraft:container_click", 1L, "CLIENTBOUND:minecraft:container_set_slot", 1L);
    }
    @Test void inventoryPresenceOrMovementPacketsNeverProveOrganizing() {
        var before = inventory(Map.of("iron_pickaxe", 1), "empty", "pickaxe");
        var after = inventory(Map.of("iron_pickaxe", 1), "pickaxe", "empty");
        var p = new FocusedActivityProof.Progress();
        assertFalse(FocusedActivityProof.verify("organize_inventory", ParamValues.EMPTY, before, before, p, transferPackets()).confirmed());
        assertFalse(FocusedActivityProof.verify("organize_inventory", ParamValues.EMPTY, before, after, p,
                Map.of("SERVERBOUND:minecraft:move_player_pos", 100L, "CLIENTBOUND:minecraft:keep_alive", 100L)).confirmed());
        // A correctly predicted player-inventory swap need not produce an
        // inbound slot/content correction packet.
        var verdict = FocusedActivityProof.verify("organize_inventory", ParamValues.EMPTY, before, after, p,
                Map.of("SERVERBOUND:minecraft:container_click", 1L));
        assertTrue(verdict.confirmed());
        assertEquals("inventory_organize", verdict.mechanic().get("id"));
        assertEquals(before.slots(), verdict.mechanic().get("beforeSlots"));
        assertEquals(after.slots(), verdict.mechanic().get("afterSlots"));
        assertEquals("client_action_and_task_outcome", verdict.mechanic().get("source"));
        assertFalse(verdict.mechanic().containsKey("serverConfirmed"));
    }
    @Test void internalFishingTimeoutCannotMasqueradeAsCompletedFishingGoal() {
        var empty = inventory(Map.of());
        var caught = inventory(Map.of("cod", 2));
        var p = new FocusedActivityProof.Progress(); p.bobberSeen = true;
        var packets = Map.of("SERVERBOUND:minecraft:use_item", 3L);
        var params = new ParamValues(Map.of("count", 2));
        assertFalse(FocusedActivityProof.verify("fish", params, empty, empty, p, packets).confirmed());
        assertTrue(FocusedActivityProof.verify("fish", params, empty, caught, p, packets).confirmed());
        p.bobberSeen = false;
        assertFalse(FocusedActivityProof.verify("fish", params, empty, caught, p, packets).confirmed());
    }
    @Test void containerDepositNeedsObservedTransferAndRequestedQuantity() {
        var start = inventory(Map.of("dirt", 4));
        var end = inventory(Map.of("dirt", 1));
        var p = new FocusedActivityProof.Progress(); p.inventory(start);
        var params = new ParamValues(Map.of("item", "dirt", "count", 3));
        assertFalse(FocusedActivityProof.verify("chest_store_items", params, start, end, p, transferPackets()).confirmed());
        p.screens.add("GenericContainerScreenHandler");
        assertTrue(FocusedActivityProof.verify("chest_store_items", params, start, end, p, transferPackets()).confirmed());
        assertFalse(FocusedActivityProof.verify("chest_store_items", params, start, start, p, transferPackets()).confirmed());
    }
    @Test void smeltingRequiresNewOutputAndFurnaceInteraction() {
        var start = inventory(Map.of("iron_ingot", 4));
        var end = inventory(Map.of("iron_ingot", 6));
        var p = new FocusedActivityProof.Progress();
        var params = new ParamValues(Map.of("count", 2));
        assertFalse(FocusedActivityProof.verify("smelt_iron_ingot", params, start, end, p, transferPackets()).confirmed());
        p.screens.add("FurnaceScreenHandler");
        assertTrue(FocusedActivityProof.verify("smelt_iron_ingot", params, start, end, p, transferPackets()).confirmed());
        assertFalse(FocusedActivityProof.verify("smelt_iron_ingot", params, start, start, p, transferPackets()).confirmed());
    }
    @Test void buildingAndCombatRequireSuccessfulGoalOracleInAdditionToActions() {
        var empty = inventory(Map.of()); var p = new FocusedActivityProof.Progress(); p.placementChildSeen = true;
        var packets = Map.of("SERVERBOUND:minecraft:use_item_on", 4L, "CLIENTBOUND:minecraft:block_update", 4L,
                "SERVERBOUND:minecraft:interact", 4L);
        assertFalse(FocusedActivityProof.verify("build_hut", ParamValues.EMPTY, empty, empty, p, packets).confirmed());
        assertFalse(FocusedActivityProof.verify("slay_mob", ParamValues.EMPTY, empty, empty, p, packets).confirmed());
        p.taskOracle = true;
        assertTrue(FocusedActivityProof.verify("build_hut", ParamValues.EMPTY, empty, empty, p, packets).confirmed());
        assertTrue(FocusedActivityProof.verify("slay_mob", ParamValues.EMPTY, empty, empty, p, packets).confirmed());
    }
    @Test void boatingTimeoutOrWalkingToTheTargetDoesNotProveVehicleGoal() {
        var empty = inventory(Map.of()); var p = new FocusedActivityProof.Progress(); p.distanceToTravelTarget = 5;
        var packets = Map.of("SERVERBOUND:minecraft:paddle_boat", 50L);
        assertFalse(FocusedActivityProof.verify("travel_boat_cross", ParamValues.EMPTY, empty, empty, p, packets).confirmed());
        p.boatSeen = true; p.boatDistance = 20;
        assertTrue(FocusedActivityProof.verify("travel_boat_cross", ParamValues.EMPTY, empty, empty, p, packets).confirmed());
        p.distanceToTravelTarget = 200;
        assertFalse(FocusedActivityProof.verify("travel_boat_cross", ParamValues.EMPTY, empty, empty, p, packets).confirmed());
    }
    @Test void providerAndOtherChainPacketsCannotProveTheGoal() {
        var snapshot = new PacketCoverage.Snapshot(767, "test", List.of(
                new PacketCoverage.Entry("PLAY", "SERVERBOUND", "minecraft:container_click", "activity.fish.provider.1", "FocusedActivityTask", "none", 100),
                new PacketCoverage.Entry("PLAY", "SERVERBOUND", "minecraft:container_click", "activity.fish.goal", "OtherTask", "none", 100),
                new PacketCoverage.Entry("PLAY", "SERVERBOUND", "minecraft:use_item", "activity.fish.goal", "FocusedActivityTask", "none", 2)), List.of());
        assertEquals(Map.of("SERVERBOUND:minecraft:use_item", 2L),
                FocusedActivityTask.scopedPackets(snapshot, "activity.fish.goal", "FocusedActivityTask"));
    }
    @Test void screenEvidenceUsesTypedIdentityThatSurvivesProductionRemapping() {
        assertEquals("FurnaceScreenHandler", FocusedActivityTask.screenKind(FurnaceScreenHandler.class));
        assertEquals("FurnaceScreenHandler", FocusedActivityTask.screenKind(BlastFurnaceScreenHandler.class));
        assertEquals("FurnaceScreenHandler", FocusedActivityTask.screenKind(SmokerScreenHandler.class));
        assertEquals("MerchantScreenHandler", FocusedActivityTask.screenKind(MerchantScreenHandler.class));
        assertEquals("EnchantmentScreenHandler", FocusedActivityTask.screenKind(EnchantmentScreenHandler.class));
        assertEquals("AnvilScreenHandler", FocusedActivityTask.screenKind(AnvilScreenHandler.class));
        assertEquals("GrindstoneScreenHandler", FocusedActivityTask.screenKind(GrindstoneScreenHandler.class));
        assertEquals("GenericContainerScreenHandler", FocusedActivityTask.screenKind(GenericContainerScreenHandler.class));
        assertEquals("ShulkerBoxScreenHandler", FocusedActivityTask.screenKind(ShulkerBoxScreenHandler.class));
        assertEquals("other", FocusedActivityTask.screenKind(PlayerScreenHandler.class));
    }
}
