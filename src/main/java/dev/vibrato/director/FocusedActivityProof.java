package dev.vibrato.director;

import java.util.*;

/** Conservative, pure proof rules: a requested mechanic is never evidence by itself. */
final class FocusedActivityProof {
    static final Map<String, String> MECHANICS = Map.ofEntries(
            Map.entry("organize_inventory", "inventory_organize"),
            Map.entry("chest_store_items", "container_deposit"), Map.entry("inventory_stash_items", "container_deposit"),
            Map.entry("place_chest_and_stash", "container_deposit"), Map.entry("stash_valuables", "container_deposit"),
            Map.entry("loot_nearby_chests", "container_withdraw"), Map.entry("loot_chests", "container_withdraw"),
            Map.entry("smelt_iron_ingot", "smelt"), Map.entry("smelt_gold_ingot", "smelt"),
            Map.entry("smelt_glass", "smelt"), Map.entry("smelt_charcoal", "smelt"),
            Map.entry("fish", "fish"), Map.entry("trade_for_item", "trade"), Map.entry("trade_for_enchanted_book", "trade"),
            Map.entry("enchant_item", "enchant"), Map.entry("anvil_rename", "anvil_rename"),
            Map.entry("grindstone_disenchant", "grindstone"), Map.entry("slay_mob", "attack_entity"),
            Map.entry("breed_animals", "breed_animal"), Map.entry("farm_crop", "farm_crop"),
            Map.entry("build_hut", "place_block"), Map.entry("build_wall", "place_block"),
            Map.entry("travel_boat_cross", "ride_vehicle"));

    record Inventory(Map<String, Integer> counts, List<String> slots, Map<String, Integer> enchanted, Map<String, Integer> named) {
        int count(String item) { return counts.getOrDefault(item, 0); }
        int total() { return counts.values().stream().mapToInt(Integer::intValue).sum(); }
    }

    static final class Progress {
        final Set<String> screens = new HashSet<>();
        final Map<String, Integer> maximum = new HashMap<>();
        int maximumTotal;
        boolean bobberSeen, placementChildSeen, boatSeen;
        double boatDistance, distanceToTravelTarget = Double.POSITIVE_INFINITY;
        int traded;
        boolean taskOracle;
        void inventory(Inventory value) {
            value.counts().forEach((item, count) -> maximum.merge(item, count, Math::max));
            maximumTotal = Math.max(maximumTotal, value.total());
        }
    }

    record Verdict(boolean confirmed, String reason, Map<String, Object> mechanic) {}

    static Verdict verify(String id, ParamValues params, Inventory before, Inventory after, Progress p,
                          Map<String, Long> packets) {
        String mechanic = MECHANICS.get(id);
        if (mechanic == null) return new Verdict(false, "activity has no completion proof adapter", Map.of());
        boolean sync = packet(packets, "CLIENTBOUND", "container_set_slot", "container_set_content");
        boolean click = packet(packets, "SERVERBOUND", "container_click");
        boolean blockUse = packet(packets, "SERVERBOUND", "use_item_on");
        boolean entityUse = packet(packets, "SERVERBOUND", "interact");
        boolean outcome = switch (id) {
            case "organize_inventory" -> click && sync && !before.slots().equals(after.slots());
            case "chest_store_items", "inventory_stash_items" -> click && sync && storageScreen(p)
                    && p.maximum.getOrDefault(params.getString("item"), 0) - after.count(params.getString("item")) >= params.getInt("count");
            case "place_chest_and_stash", "stash_valuables" -> click && sync && storageScreen(p)
                    && p.maximumTotal - after.total() >= (params.has("min_items") ? params.getInt("min_items") : 1);
            case "loot_nearby_chests", "loot_chests" -> click && sync && storageScreen(p) && after.total() > before.total();
            case "smelt_iron_ingot", "smelt_gold_ingot", "smelt_glass", "smelt_charcoal" -> {
                String product = id.substring("smelt_".length());
                yield click && sync && p.screens.contains("FurnaceScreenHandler")
                        && after.count(product) - before.count(product) >= params.getInt("count");
            }
            case "fish" -> p.bobberSeen && packet(packets, "SERVERBOUND", "use_item")
                    && fish(after) - fish(before) >= params.getInt("count");
            case "trade_for_item", "trade_for_enchanted_book" -> p.taskOracle && p.traded >= params.getInt("count")
                    && p.screens.contains("MerchantScreenHandler") && click && sync
                    && packet(packets, "SERVERBOUND", "select_trade");
            case "enchant_item" -> p.taskOracle && p.screens.contains("EnchantmentScreenHandler") && sync
                    && packet(packets, "SERVERBOUND", "container_button_click")
                    && after.enchanted().getOrDefault(enchantOutput(params.getString("item")), 0)
                    > before.enchanted().getOrDefault(enchantOutput(params.getString("item")), 0);
            case "anvil_rename" -> p.taskOracle && p.screens.contains("AnvilScreenHandler") && sync
                    && packet(packets, "SERVERBOUND", "rename_item")
                    && after.named().getOrDefault(params.getString("item") + ":" + params.getString("name"), 0)
                    > before.named().getOrDefault(params.getString("item") + ":" + params.getString("name"), 0);
            case "grindstone_disenchant" -> p.taskOracle && p.screens.contains("GrindstoneScreenHandler") && click && sync
                    && after.enchanted().getOrDefault(params.getString("item"), 0) < before.enchanted().getOrDefault(params.getString("item"), 0)
                    && after.count(params.getString("item")) >= before.count(params.getString("item"));
            case "slay_mob", "breed_animals" -> p.taskOracle && entityUse;
            case "farm_crop" -> blockUse && packet(packets, "SERVERBOUND", "player_action")
                    && after.count(params.getString("crop")) - before.count(params.getString("crop")) >= params.getInt("rows") * 9;
            case "build_hut", "build_wall" -> p.taskOracle && p.placementChildSeen && blockUse
                    && packet(packets, "CLIENTBOUND", "block_update", "section_blocks_update");
            case "travel_boat_cross" -> p.boatSeen && p.boatDistance >= 8 && p.distanceToTravelTarget <= 20
                    && packet(packets, "SERVERBOUND", "paddle_boat", "move_vehicle");
            default -> false;
        };
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("id", mechanic); evidence.put("success", outcome); evidence.put("actionObserved", outcome);
        evidence.put("goalTaskFinished", true); evidence.put("source", "client_action_and_task_outcome");
        evidence.put("packetDeltas", packets);
        return new Verdict(outcome, outcome ? "goal finished with mechanic-specific action and outcome" : "goal finished without sufficient mechanic-specific proof", evidence);
    }

    private static boolean storageScreen(Progress p) {
        return p.screens.contains("GenericContainerScreenHandler") || p.screens.contains("ShulkerBoxScreenHandler");
    }
    private static int fish(Inventory inventory) {
        return inventory.count("cod") + inventory.count("salmon") + inventory.count("tropical_fish") + inventory.count("pufferfish");
    }
    private static String enchantOutput(String item) { return item.equals("book") ? "enchanted_book" : item; }
    private static boolean packet(Map<String, Long> packets, String side, String... ids) {
        return Arrays.stream(ids).anyMatch(id -> packets.getOrDefault(side + ":minecraft:" + id, 0L) > 0);
    }
}
