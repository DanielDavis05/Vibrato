package dev.vibrato.director;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.enchant.AnvilCombineTask;
import dev.vibrato.tasks.enchant.BrewPotionTask;
import dev.vibrato.tasks.enchant.CollectXpTask;
import dev.vibrato.tasks.enchant.EnchantItemTask;
import dev.vibrato.tasks.enchant.GrindstoneTask;
import dev.vibrato.tasks.enchant.PotionRecipes.Potion;
import dev.vibrato.tasks.enchant.RenameInAnvilTask;
import dev.vibrato.tasks.trade.SellToVillagerTask;
import dev.vibrato.tasks.trade.TradeWithVillagerTask;
import dev.vibrato.tasksystem.Task;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Owned by workstream T1 (see PLAN.md). Register this domain's activities here. */
public final class TradeEnchantActivities {

    private static final Fact VILLAGE = Fact.of("village:found");

    private TradeEnchantActivities() {}

    public static void register(ActivityRegistry registry) {
        registry.register(spec("trade_for_item", FeatureArea.TRADE, Set.of(VILLAGE), Set.of(Fact.of("villager:traded")),
                List.of(
                        ParamSpec.choice("item", "bread", "paper", "book", "arrow", "glass_bottle", "compass", "clock",
                                "map", "lantern", "saddle", "ender_pearl", "name_tag"),
                        ParamSpec.ints("count", 1, 16)),
                v -> new TradeWithVillagerTask(item(v.getString("item")), v.getInt("count")),
                v -> Duration.ofMinutes(6)));

        registry.register(spec("trade_for_enchanted_book", FeatureArea.TRADE, Set.of(VILLAGE), Set.of(Fact.of("villager:traded")),
                List.of(ParamSpec.ints("count", 1, 3)),
                v -> new TradeWithVillagerTask(Items.ENCHANTED_BOOK, v.getInt("count")),
                v -> Duration.ofMinutes(8)));

        registry.register(spec("sell_surplus", FeatureArea.TRADE, Set.of(VILLAGE), Set.of(Fact.of("villager:traded")),
                List.of(
                        ParamSpec.choice("item", "wheat", "carrot", "potato", "beetroot", "paper", "coal", "string", "sugar_cane"),
                        ParamSpec.ints("count", 1, 32)),
                v -> new SellToVillagerTask(item(v.getString("item")), v.getInt("count")),
                v -> Duration.ofMinutes(4)));

        registry.register(spec("get_emeralds", FeatureArea.TRADE, Set.of(), Set.of(Fact.has("emerald")),
                List.of(ParamSpec.ints("count", 4, 64)),
                v -> TaskCatalogue.getItemTask("emerald", v.getInt("count")),
                v -> Duration.ofMinutes(8)));

        registry.register(spec("enchant_item", FeatureArea.ENCHANT, Set.of(), Set.of(Fact.of("gear:enchanted")),
                List.of(
                        ParamSpec.choice("item", "iron_pickaxe", "iron_sword", "iron_axe", "iron_chestplate",
                                "iron_helmet", "iron_boots", "bow", "book"),
                        ParamSpec.ints("minLevel", 1, 30)),
                v -> new EnchantItemTask(item(v.getString("item")), v.getInt("minLevel")),
                v -> Duration.ofMinutes(8)));

        registry.register(spec("get_lapis", FeatureArea.ENCHANT, Set.of(), Set.of(Fact.has("lapis_lazuli")),
                List.of(ParamSpec.ints("count", 3, 32)),
                v -> TaskCatalogue.getItemTask("lapis_lazuli", v.getInt("count")),
                v -> Duration.ofMinutes(6)));

        registry.register(spec("brew_potion", FeatureArea.ENCHANT, Set.of(), Set.of(Fact.of("potion:brewed")),
                List.of(ParamSpec.choice("type", "awkward", "healing", "speed", "strength", "fire_resistance",
                        "night_vision", "swiftness_extended")),
                v -> new BrewPotionTask(Potion.valueOf(v.getString("type").toUpperCase())),
                v -> Duration.ofMinutes(12)));

        registry.register(spec("anvil_combine", FeatureArea.GEAR, Set.of(), Set.of(Fact.of("gear:repaired")),
                List.of(
                        ParamSpec.choice("target", "iron_pickaxe", "iron_sword", "iron_chestplate", "iron_helmet",
                                "iron_boots", "bow"),
                        ParamSpec.choice("second", "enchanted_book", "iron_ingot", "diamond")),
                v -> new AnvilCombineTask(item(v.getString("target")), item(v.getString("second"))),
                v -> Duration.ofMinutes(6)));

        registry.register(spec("anvil_rename", FeatureArea.GEAR, Set.of(), Set.of(),
                List.of(
                        ParamSpec.choice("item", "iron_pickaxe", "iron_sword", "iron_axe", "iron_chestplate",
                                "iron_helmet", "iron_boots"),
                        ParamSpec.choice("name", "Excalibur", "Gandalf's Staff", "Dirt Pick", "Lucky Blade",
                                "Miner's Friend", "Bear Crusher", "Bow of Dawn", "Sentinel")),
                v -> new RenameInAnvilTask(item(v.getString("item")), v.getString("name")),
                v -> Duration.ofMinutes(4)));

        registry.register(spec("grindstone_disenchant", FeatureArea.GEAR, Set.of(), Set.of(),
                List.of(ParamSpec.choice("item", "iron_pickaxe", "iron_sword", "iron_axe", "iron_chestplate",
                        "iron_helmet", "iron_boots", "bow")),
                v -> new GrindstoneTask(item(v.getString("item"))),
                v -> Duration.ofMinutes(4)));

        registry.register(spec("collect_xp_levels", FeatureArea.LEVEL, Set.of(), Set.of(),
                List.of(ParamSpec.ints("level", 5, 30)),
                v -> new CollectXpTask(v.getInt("level")),
                v -> Duration.ofMinutes(6)));
    }

    private static Item item(String id) {
        return Registries.ITEM.get(Identifier.of("minecraft", id));
    }

    private static Spec spec(String id, FeatureArea area, Set<Fact> requires, Set<Fact> provides,
                             List<ParamSpec> params, Function<ParamValues, Task> task, Function<ParamValues, Duration> budget) {
        return new Spec(id, area, requires, provides, params, task, budget);
    }

    /** One stateless activity: the lambdas only read their parameters and build fresh tasks. */
    private record Spec(String id, FeatureArea area, Set<Fact> requires, Set<Fact> provides, List<ParamSpec> params,
                        Function<ParamValues, Task> task, Function<ParamValues, Duration> time) implements Activity {

        @Override
        public Task create(Vibrato mod, ParamValues values) {
            return task.apply(values);
        }

        @Override
        public Duration budget(ParamValues values) {
            return time.apply(values);
        }
    }
}
