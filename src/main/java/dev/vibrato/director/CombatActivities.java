package dev.vibrato.director;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.combat.BowFightTask;
import dev.vibrato.tasks.combat.ClearNearbyHostilesTask;
import dev.vibrato.tasks.combat.DefendPositionTask;
import dev.vibrato.tasks.combat.FarmMobDropsTask;
import dev.vibrato.tasks.combat.HuntForFoodTask;
import dev.vibrato.tasks.combat.SlayMobTask;
import dev.vibrato.tasks.combat.UpgradeGearTask;
import dev.vibrato.tasks.combat.UpgradeGearTask.GearTier;
import dev.vibrato.tasks.resources.CollectBlazeRodsTask;
import dev.vibrato.tasksystem.Task;
import net.minecraft.item.Items;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Owned by workstream T6 (see PLAN.md). Register this domain's activities here. */
public final class CombatActivities {

    private static final List<String> HOSTILE_MOBS = List.of(
            "zombie", "skeleton", "creeper", "spider", "enderman", "witch", "blaze", "drowned", "phantom", "slime");
    private static final List<String> ANIMALS = List.of("cow", "pig", "sheep", "chicken", "rabbit");
    private static final List<String> FARM_DROPS = List.of(
            "bone", "arrow", "string", "spider_eye", "gunpowder", "ender_pearl", "slime_ball", "leather", "feather", "ink_sac");

    private static final Fact IRON_PICKAXE = Fact.has("iron_pickaxe");
    private static final Fact DIAMOND_PICKAXE = Fact.has("diamond_pickaxe");
    private static final Fact NETHER = Fact.dimension("nether");

    private CombatActivities() {}

    public static void register(ActivityRegistry registry) {
        registry.register(spec("slay_mob", FeatureArea.COMBAT, Set.of(), Set.of(),
                List.of(ParamSpec.choice("mob", HOSTILE_MOBS.toArray(String[]::new)), ParamSpec.ints("count", 1, 12)),
                v -> new SlayMobTask(mobType(v.getString("mob")), v.getInt("count")),
                v -> Duration.ofSeconds(SlayMobTask.timeLimitSeconds(v.getInt("count")))));

        registry.register(spec("hunt_animals", FeatureArea.GATHER, Set.of(), Set.of(),
                List.of(ParamSpec.choice("animal", ANIMALS.toArray(String[]::new)), ParamSpec.ints("count", 1, 16)),
                v -> new FarmMobDropsTask(rawMeat(v.getString("animal")), v.getInt("count")),
                v -> Duration.ofMinutes(3 + v.getInt("count") / 2)));

        registry.register(spec("farm_drop", FeatureArea.GATHER, Set.of(), Set.of(),
                List.of(ParamSpec.choice("item", FARM_DROPS.toArray(String[]::new)), ParamSpec.ints("count", 4, 32)),
                v -> new FarmMobDropsTask(item(v.getString("item")), v.getInt("count")),
                v -> Duration.ofMinutes(4 + v.getInt("count") / 8)));

        registry.register(spec("blaze_rods", FeatureArea.GATHER, Set.of(NETHER), Set.of(Fact.has("blaze_rod")),
                List.of(ParamSpec.ints("count", 4, 12)),
                v -> new CollectBlazeRodsTask(v.getInt("count")),
                v -> Duration.ofMinutes(5 + v.getInt("count") / 2)));

        registry.register(spec("bow_practice", FeatureArea.COMBAT, Set.of(), Set.of(Fact.has("bow")),
                List.of(ParamSpec.choice("mob", "zombie", "creeper", "spider", "witch", "blaze", "phantom")),
                v -> new BowFightTask(mobType(v.getString("mob"))),
                v -> Duration.ofMinutes(4)));

        registry.register(spec("upgrade_iron_armor", FeatureArea.GEAR, Set.of(),
                Set.of(Fact.of("armor:iron"), Fact.of("tool:iron")), List.of(),
                v -> new UpgradeGearTask(GearTier.IRON),
                v -> Duration.ofMinutes(10)));

        registry.register(spec("upgrade_diamond_armor", FeatureArea.GEAR, Set.of(IRON_PICKAXE),
                Set.of(Fact.of("armor:diamond"), Fact.of("tool:diamond")), List.of(),
                v -> new UpgradeGearTask(GearTier.DIAMOND),
                v -> Duration.ofMinutes(15)));

        registry.register(spec("upgrade_netherite_armor", FeatureArea.GEAR, Set.of(Fact.of("armor:diamond"), DIAMOND_PICKAXE),
                Set.of(Fact.of("armor:netherite")), List.of(),
                v -> new UpgradeGearTask(GearTier.NETHERITE),
                v -> Duration.ofMinutes(20)));

        registry.register(spec("get_shield", FeatureArea.GEAR, Set.of(), Set.of(Fact.has("shield")), List.of(),
                v -> TaskCatalogue.getItemTask("shield", 1),
                v -> Duration.ofMinutes(4)));

        registry.register(spec("get_weapon", FeatureArea.GEAR, Set.of(), Set.of(),
                List.of(ParamSpec.choice("tier", "stone", "iron", "diamond")),
                v -> TaskCatalogue.getItemTask(v.getString("tier") + "_sword", 1),
                v -> Duration.ofMinutes(switch (v.getString("tier")) {
                    case "stone" -> 3;
                    case "iron" -> 6;
                    default -> 12;
                })));

        registry.register(spec("clear_nearby_hostiles", FeatureArea.COMBAT, Set.of(), Set.of(),
                List.of(ParamSpec.ints("radius", 8, 24)),
                v -> new ClearNearbyHostilesTask(v.getInt("radius")),
                v -> Duration.ofMinutes(4)));

        registry.register(spec("defend_position", FeatureArea.COMBAT, Set.of(), Set.of(),
                List.of(ParamSpec.ints("seconds", 30, 180)),
                v -> new DefendPositionTask(v.getInt("seconds")),
                v -> Duration.ofSeconds(v.getInt("seconds") + 180)));

        registry.register(spec("hunt_for_food", FeatureArea.GATHER, Set.of(), Set.of(Fact.of("food:stocked")),
                List.of(ParamSpec.ints("units", 8, 40)),
                v -> new HuntForFoodTask(v.getInt("units")),
                v -> Duration.ofMinutes(5 + v.getInt("units") / 10)));
    }

    private static EntityType<?> mobType(String name) {
        return switch (name) {
            case "zombie" -> EntityType.ZOMBIE;
            case "skeleton" -> EntityType.SKELETON;
            case "creeper" -> EntityType.CREEPER;
            case "spider" -> EntityType.SPIDER;
            case "enderman" -> EntityType.ENDERMAN;
            case "witch" -> EntityType.WITCH;
            case "blaze" -> EntityType.BLAZE;
            case "drowned" -> EntityType.DROWNED;
            case "phantom" -> EntityType.PHANTOM;
            case "slime" -> EntityType.SLIME;
            default -> throw new IllegalArgumentException("Unknown mob " + name);
        };
    }

    private static Item rawMeat(String animal) {
        return switch (animal) {
            case "cow" -> Items.BEEF;
            case "pig" -> Items.PORKCHOP;
            case "sheep" -> Items.MUTTON;
            case "chicken" -> Items.CHICKEN;
            case "rabbit" -> Items.RABBIT;
            default -> throw new IllegalArgumentException("Unknown animal " + animal);
        };
    }

    private static Item item(String id) {
        return Registries.ITEM.get(Identifier.of("minecraft", id));
    }

    private static Spec spec(String id, FeatureArea area, Set<Fact> requires, Set<Fact> provides,
                             List<ParamSpec> params, Function<ParamValues, Task> task, Function<ParamValues, Duration> time) {
        return new Spec(id, area, requires, provides, params, task, time);
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
