package dev.vibrato.director;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.advancement.AdvancementGoals;
import dev.vibrato.advancement.PursueAdvancementTask;
import dev.vibrato.Vibrato;
import dev.vibrato.nav.EscapeUndergroundTask;
import dev.vibrato.tasks.organize.LootNearbyChestsTask;
import dev.vibrato.tasks.construction.compound.ConstructNetherPortalObsidianTask;
import dev.vibrato.tasks.container.SmeltInFurnaceTask;
import dev.vibrato.tasks.container.StoreInAnyContainerTask;
import dev.vibrato.tasks.misc.EquipArmorTask;
import dev.vibrato.tasks.misc.PlaceBedAndSetSpawnTask;
import dev.vibrato.tasks.misc.SleepThroughNightTask;
import dev.vibrato.tasks.movement.DefaultGoToDimensionTask;
import dev.vibrato.tasks.movement.GetToXZTask;
import dev.vibrato.tasks.resources.CollectFoodTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.Dimension;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.SmeltTarget;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Wraps the existing catalogue, smelting, equipment, storage, food, movement and dimension tasks as activities.
 * Each activity builds its task only in {@link Activity#create}; everything else here is symbolic.
 */
public final class CoreActivities {

    private static final String[] GATHER_ITEMS = {
            "cobblestone", "sand", "gravel", "dirt", "coal", "raw_iron", "raw_gold", "raw_copper", "iron_ingot",
            "gold_ingot", "diamond", "redstone", "lapis_lazuli", "emerald", "quartz", "amethyst_shard", "flint",
            "string", "feather", "leather", "gunpowder", "bone", "ender_pearl", "slime_ball", "blaze_rod", "obsidian",
            "glowstone_dust", "sugar_cane", "cactus", "honeycomb", "netherite_scrap", "ancient_debris", "netherrack",
            "ink_sac", "cobweb", "clay", "egg"
    };
    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
    private static final String[] CROPS = {"wheat", "carrot", "potato", "beetroot", "sugar_cane", "melon", "pumpkin", "cocoa_beans"};
    private static final String[] STORED_ITEMS = {
            "cobblestone", "dirt", "sand", "gravel", "coal", "iron_ingot", "gold_ingot", "diamond", "redstone", "string"
    };
    private static final int SMELT_BASE_MINUTES = 4;
    private static final int ORE_SMELT_BASE_MINUTES = 12;
    private static final List<ParamSpec> SMELT_COUNT = List.of(ParamSpec.ints("count", 1, 64));

    private CoreActivities() {}

    public static void register(ActivityRegistry registry) {
        registerGather(registry);
        registerCraft(registry);
        registerSmelt(registry);
        registerGear(registry);
        registerStorage(registry);
        registerFarm(registry);
        registerExplore(registry);
        registerRare(registry);
        registerAdvancement(registry);
        registerCraftedRare(registry);
        registerTransport(registry);
        registerSleep(registry);
    }

    private static void registerGather(ActivityRegistry registry) {
        registry.register(new CoreActivity("gather_wood", FeatureArea.GATHER, Set.of(), Set.of(), List.of(
                ParamSpec.choice("wood", WOODS),
                ParamSpec.ints("count", 1, 64)),
                values -> Duration.ofMinutes(Math.min(15, 2 + values.getInt("count") / 16)),
                (mod, values) -> TaskCatalogue.getItemTask(values.getString("wood") + "_log", values.getInt("count"))));

        registry.register(new CoreActivity("gather_item", FeatureArea.GATHER, Set.of(), Set.of(), List.of(
                ParamSpec.choice("item", GATHER_ITEMS),
                ParamSpec.ints("count", 1, 64)),
                values -> Duration.ofMinutes(Math.min(15, 3 + values.getInt("count") / 8)),
                (mod, values) -> TaskCatalogue.getItemTask(values.getString("item"), values.getInt("count"))));
    }

    private static void registerCraft(ActivityRegistry registry) {
        fixed(registry, "craft_crafting_table", FeatureArea.CRAFT, Set.of(Fact.has("crafting_table")), 3,
                () -> TaskCatalogue.getItemTask("crafting_table", 1));
        fixed(registry, "craft_wooden_pickaxe", FeatureArea.CRAFT, Set.of(Fact.has("wooden_pickaxe")), 3,
                () -> TaskCatalogue.getItemTask("wooden_pick", 1));
        fixed(registry, "craft_stone_pickaxe", FeatureArea.CRAFT, Set.of(Fact.has("stone_pickaxe"), WorldFacts.BASIC_PICKAXE), 4,
                () -> TaskCatalogue.getItemTask("stone_pick", 1));
        fixed(registry, "craft_iron_pickaxe", FeatureArea.CRAFT, Set.of(Fact.has("iron_pickaxe"), Fact.of("tool:iron"), WorldFacts.BASIC_PICKAXE), 6,
                () -> TaskCatalogue.getItemTask("iron_pick", 1));
        fixed(registry, "craft_diamond_pickaxe", FeatureArea.CRAFT, Set.of(Fact.has("diamond")), Set.of(Fact.has("diamond_pickaxe"), Fact.of("tool:diamond"), WorldFacts.BASIC_PICKAXE), 10,
                () -> TaskCatalogue.getItemTask("diamond_pick", 1));
        registry.register(new CoreActivity("craft_iron_tool", FeatureArea.CRAFT, Set.of(), Set.of(Fact.of("tool:iron")), List.of(
                ParamSpec.choice("tool", "sword", "axe", "shovel", "hoe")),
                values -> Duration.ofMinutes(6),
                (mod, values) -> TaskCatalogue.getItemTask("iron_" + values.getString("tool"), 1)));
        registry.register(new CoreActivity("craft_diamond_tool", FeatureArea.CRAFT, Set.of(Fact.has("diamond")), Set.of(Fact.of("tool:diamond")), List.of(
                ParamSpec.choice("tool", "sword", "axe", "shovel", "hoe")),
                values -> Duration.ofMinutes(10),
                (mod, values) -> TaskCatalogue.getItemTask("diamond_" + values.getString("tool"), 1)));
        fixed(registry, "craft_iron_armor_set", FeatureArea.CRAFT, Set.of(Fact.of("armor:iron")), 12,
                () -> TaskCatalogue.getSquashedItemTask(
                        TaskCatalogue.getItemTarget("iron_helmet", 1),
                        TaskCatalogue.getItemTarget("iron_chestplate", 1),
                        TaskCatalogue.getItemTarget("iron_leggings", 1),
                        TaskCatalogue.getItemTarget("iron_boots", 1)));
        fixed(registry, "craft_diamond_armor_set", FeatureArea.CRAFT, Set.of(Fact.has("diamond")), Set.of(Fact.of("armor:diamond")), 15,
                () -> TaskCatalogue.getSquashedItemTask(
                        TaskCatalogue.getItemTarget("diamond_helmet", 1),
                        TaskCatalogue.getItemTarget("diamond_chestplate", 1),
                        TaskCatalogue.getItemTarget("diamond_leggings", 1),
                        TaskCatalogue.getItemTarget("diamond_boots", 1)));
        fixed(registry, "craft_bucket", FeatureArea.CRAFT, Set.of(Fact.has("bucket")), 6,
                () -> TaskCatalogue.getItemTask("bucket", 1));
        fixed(registry, "craft_shield", FeatureArea.CRAFT, Set.of(Fact.has("shield")), 6,
                () -> TaskCatalogue.getItemTask("shield", 1));
        fixed(registry, "craft_chest", FeatureArea.CRAFT, Set.of(Fact.has("chest")), 4,
                () -> TaskCatalogue.getItemTask("chest", 1));
        fixed(registry, "craft_furnace", FeatureArea.CRAFT, Set.of(Fact.has("furnace")), 4,
                () -> TaskCatalogue.getItemTask("furnace", 1));
        fixed(registry, "craft_bed", FeatureArea.CRAFT, Set.of(Fact.has("wool")), Set.of(Fact.has("bed")), 8,
                () -> TaskCatalogue.getItemTask("bed", 1));
        registry.register(new CoreActivity("craft_torches", FeatureArea.CRAFT, Set.of(), Set.of(Fact.has("torch")), List.of(
                ParamSpec.ints("count", 1, 64)),
                values -> Duration.ofMinutes(Math.min(15, 3 + values.getInt("count") / 16)),
                (mod, values) -> TaskCatalogue.getItemTask("torch", values.getInt("count"))));
    }

    private static void registerSmelt(ActivityRegistry registry) {
        registry.register(new CoreActivity("smelt_iron_ingot", FeatureArea.SMELT, Set.of(), Set.of(Fact.has("iron_ingot")),
                SMELT_COUNT, values -> smeltBudget(values, ORE_SMELT_BASE_MINUTES),
                (mod, values) -> smelt(values, Items.IRON_INGOT, "raw_iron", Items.IRON_ORE)));
        registry.register(new CoreActivity("smelt_gold_ingot", FeatureArea.SMELT, Set.of(), Set.of(Fact.has("gold_ingot")),
                SMELT_COUNT, values -> smeltBudget(values, ORE_SMELT_BASE_MINUTES),
                (mod, values) -> smelt(values, Items.GOLD_INGOT, "raw_gold")));
        registry.register(new CoreActivity("smelt_glass", FeatureArea.SMELT, Set.of(), Set.of(Fact.has("glass")),
                SMELT_COUNT, values -> smeltBudget(values, SMELT_BASE_MINUTES),
                (mod, values) -> smelt(values, Items.GLASS, "sand")));
        registry.register(new CoreActivity("smelt_charcoal", FeatureArea.SMELT, Set.of(), Set.of(Fact.has("charcoal")),
                SMELT_COUNT, values -> smeltBudget(values, SMELT_BASE_MINUTES),
                (mod, values) -> smelt(values, Items.CHARCOAL, "log")));
    }

    private static void registerGear(ActivityRegistry registry) {
        fixed(registry, "equip_iron_armor", FeatureArea.GEAR, Set.of(Fact.of("armor:iron")), 6,
                () -> new EquipArmorTask(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS));
        fixed(registry, "equip_diamond_armor", FeatureArea.GEAR, Set.of(Fact.of("armor:diamond")), 10,
                () -> new EquipArmorTask(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS));
    }

    private static void registerStorage(ActivityRegistry registry) {
        registry.register(new CoreActivity("loot_nearby_chests", FeatureArea.INVENTORY, Set.of(), Set.of(), List.of(
                ParamSpec.ints("radius", 16, 48)),
                values -> Duration.ofMinutes(8),
                (mod, values) -> new LootNearbyChestsTask(values.getInt("radius"))));

        registry.register(new CoreActivity("chest_store_items", FeatureArea.CHEST, Set.of(), Set.of(), List.of(
                ParamSpec.choice("item", STORED_ITEMS),
                ParamSpec.ints("count", 1, 32)),
                values -> Duration.ofMinutes(Math.min(15, 4 + values.getInt("count") / 8)),
                (mod, values) -> new StoreInAnyContainerTask(true,
                        new ItemTarget(values.getString("item"), values.getInt("count")))));

        registry.register(new CoreActivity("inventory_stash_items", FeatureArea.INVENTORY, Set.of(), Set.of(), List.of(
                ParamSpec.choice("item", STORED_ITEMS),
                ParamSpec.ints("count", 1, 64)),
                values -> Duration.ofMinutes(Math.min(10, 3 + values.getInt("count") / 16)),
                (mod, values) -> new StoreInAnyContainerTask(false,
                        new ItemTarget(values.getString("item"), values.getInt("count")))));
    }

    private static void registerFarm(ActivityRegistry registry) {
        registry.register(new CoreActivity("gather_crop", FeatureArea.FARM, Set.of(Fact.of("loc:surface")), Set.of(), List.of(
                ParamSpec.choice("crop", CROPS),
                ParamSpec.ints("count", 1, 64)),
                values -> Duration.ofMinutes(Math.min(15, 4 + values.getInt("count") / 8)),
                (mod, values) -> TaskCatalogue.getItemTask(values.getString("crop"), values.getInt("count"))));

        registry.register(new CoreActivity("harvest_food", FeatureArea.FARM, Set.of(Fact.of("loc:surface")), Set.of(Fact.of("food:stocked")), List.of(
                ParamSpec.ints("units", 4, 20)),
                values -> Duration.ofMinutes(8),
                (mod, values) -> new CollectFoodTask(values.getInt("units"))));
    }

    /** Rare materials get their own activity so the planner only chains them in when something needs them. */
    private static void registerRare(ActivityRegistry registry) {
        fixed(registry, "gather_string", FeatureArea.GATHER, Set.of(), Set.of(Fact.has("string")), 8,
                () -> TaskCatalogue.getItemTask("string", 8));
        fixed(registry, "gather_diamond", FeatureArea.GATHER, Set.of(Fact.has("iron_pickaxe")), Set.of(Fact.has("diamond")), 12,
                () -> TaskCatalogue.getItemTask("diamond", 3));
        fixed(registry, "gather_obsidian", FeatureArea.GATHER, Set.of(Fact.has("diamond_pickaxe")), Set.of(Fact.has("obsidian")), 12,
                () -> TaskCatalogue.getItemTask("obsidian", 10));
        fixed(registry, "gather_blaze_rods", FeatureArea.GATHER, Set.of(Fact.dimension("nether"), Fact.has("iron_pickaxe")),
                Set.of(Fact.has("blaze_rod")), 15, () -> TaskCatalogue.getItemTask("blaze_rod", 6));
        fixed(registry, "gather_ender_pearls", FeatureArea.GATHER, Set.of(), Set.of(Fact.has("ender_pearl")), 12,
                () -> TaskCatalogue.getItemTask("ender_pearl", 12));
    }

    /** Eyes of ender are crafted from a blaze rod and an ender pearl, both gathered by providers. */
    private static void registerCraftedRare(ActivityRegistry registry) {
        fixed(registry, "craft_ender_eye", FeatureArea.CRAFT, Set.of(Fact.has("blaze_rod"), Fact.has("ender_pearl")),
                Set.of(Fact.has("ender_eye")), 2, () -> TaskCatalogue.getItemTask("ender_eye", 1));
    }

    /** Advancement goals the bots pursue: each one provides its advancement fact once it is done. */
    private static void registerAdvancement(ActivityRegistry registry) {
        pursueAdvancement(registry, "pursue_iron_tools", "story/iron_tools");
        pursueAdvancement(registry, "pursue_mine_diamond", "story/mine_diamond");
        pursueAdvancement(registry, "pursue_lava_bucket", "story/lava_bucket");
    }

    private static void pursueAdvancement(ActivityRegistry registry, String id, String path) {
        String name = path.substring(path.indexOf('/') + 1);
        fixed(registry, id, FeatureArea.ADVANCEMENT, Set.of(), Set.of(Fact.of("advancement:" + name + ":done")), 15,
                () -> new PursueAdvancementTask(AdvancementGoals.ALL.stream()
                        .filter(goal -> goal.path().equals(path))
                        .findFirst()
                        .orElseThrow()));
    }

    private static void registerExplore(ActivityRegistry registry) {
        fixed(registry, "escape_underground", FeatureArea.EXPLORE, Set.of(), Set.of(WorldFacts.SURFACE), 8,
                EscapeUndergroundTask::new);

        registry.register(new CoreActivity("explore_offset", FeatureArea.EXPLORE, Set.of(), Set.of(), List.of(
                ParamSpec.ints("dx", -400, 400),
                ParamSpec.ints("dz", -400, 400)),
                values -> Duration.ofMinutes(Math.min(15, 3 + (Math.abs(values.getInt("dx")) + Math.abs(values.getInt("dz"))) / 100)),
                (mod, values) -> {
                    BlockPos from = mod.getPlayer().getBlockPos();
                    return new GetToXZTask(from.getX() + values.getInt("dx"), from.getZ() + values.getInt("dz"));
                }));
        registry.register(new CoreActivity("explore_far", FeatureArea.EXPLORE, Set.of(), Set.of(), List.of(
                ParamSpec.ints("dx", -1500, 1500),
                ParamSpec.ints("dz", -1500, 1500)),
                values -> Duration.ofMinutes(15),
                (mod, values) -> {
                    BlockPos from = mod.getPlayer().getBlockPos();
                    return new GetToXZTask(from.getX() + values.getInt("dx"), from.getZ() + values.getInt("dz"));
                }));
    }

    private static void registerTransport(ActivityRegistry registry) {
        registry.register(new CoreActivity("craft_boat", FeatureArea.TRANSPORT, Set.of(Fact.of("loc:surface")), Set.of(Fact.has("boat")), List.of(
                ParamSpec.choice("wood", WOODS)),
                values -> Duration.ofMinutes(4),
                (mod, values) -> TaskCatalogue.getItemTask(values.getString("wood") + "_boat", 1)));

        fixed(registry, "build_nether_portal", FeatureArea.TRANSPORT, Set.of(Fact.has("diamond_pickaxe")), Set.of(Fact.of("portal:built")), 15,
                ConstructNetherPortalObsidianTask::new);
        fixed(registry, "go_nether", FeatureArea.TRANSPORT, Set.of(Fact.has("iron_pickaxe")), Set.of(Fact.dimension("nether")), 15,
                () -> new DefaultGoToDimensionTask(Dimension.NETHER));
        registry.register(new CoreActivity("go_overworld", FeatureArea.TRANSPORT, Set.of(Fact.dimension("nether")),
                Set.of(Fact.dimension("overworld")), List.of(), values -> Duration.ofMinutes(15),
                (mod, values) -> new DefaultGoToDimensionTask(Dimension.OVERWORLD)));
    }

    private static void registerSleep(ActivityRegistry registry) {
        registry.register(new CoreActivity("place_bed_and_set_spawn", FeatureArea.SLEEP, Set.of(Fact.dimension("overworld"), Fact.of("loc:surface")),
                Set.of(Fact.of("spawn:set")), List.of(), values -> Duration.ofMinutes(8),
                (mod, values) -> new PlaceBedAndSetSpawnTask()));
        registry.register(new CoreActivity("wait_out_night", FeatureArea.SLEEP, Set.of(Fact.dimension("overworld"), Fact.of("loc:surface")),
                Set.of(), List.of(), values -> Duration.ofMinutes(15),
                (mod, values) -> new SleepThroughNightTask()));
    }

    /** Ore smelts must first gather tools, find and mine the ore, so they need far more than sand or logs. */
    private static Duration smeltBudget(ParamValues values, int baseMinutes) {
        return Duration.ofMinutes(Math.min(15, baseMinutes + values.getInt("count") / 8));
    }

    private static Task smelt(ParamValues values, Item product, String material, Item... optionalMaterials) {
        int count = values.getInt("count");
        return new SmeltInFurnaceTask(new SmeltTarget(
                new ItemTarget(product, count), new ItemTarget(material, count), optionalMaterials));
    }

    private static void fixed(ActivityRegistry registry, String id, FeatureArea area, Set<Fact> provides, int minutes,
                              Supplier<Task> task) {
        fixed(registry, id, area, Set.of(), provides, minutes, task);
    }

    private static void fixed(ActivityRegistry registry, String id, FeatureArea area, Set<Fact> requires,
                              Set<Fact> provides, int minutes, Supplier<Task> task) {
        registry.register(new CoreActivity(id, area, requires, provides, List.of(),
                values -> Duration.ofMinutes(minutes), (mod, values) -> task.get()));
    }

    /** An activity with a fixed set of facts, parameters and budget, and a factory that builds its task. */
    private record CoreActivity(String id, FeatureArea area, Set<Fact> requires, Set<Fact> provides,
                                List<ParamSpec> params, Function<ParamValues, Duration> budgetFor,
                                BiFunction<Vibrato, ParamValues, Task> factory) implements Activity {

        @Override
        public Task create(Vibrato mod, ParamValues values) {
            return factory.apply(mod, values);
        }

        @Override
        public Duration budget(ParamValues values) {
            return budgetFor.apply(values);
        }
    }
}
