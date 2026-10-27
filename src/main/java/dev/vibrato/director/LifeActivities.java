package dev.vibrato.director;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.life.BreedAnimalsTask;
import dev.vibrato.tasks.life.CookFoodTask;
import dev.vibrato.tasks.life.FarmCropsTask;
import dev.vibrato.tasks.life.FishingTask;
import dev.vibrato.tasks.life.GainXpLevelsTask;
import dev.vibrato.tasks.life.HarvestAndReplantTask;
import dev.vibrato.tasks.life.NightShelterTask;
import dev.vibrato.tasks.life.SleepInBedTask;
import dev.vibrato.tasks.misc.PlaceBedAndSetSpawnTask;
import dev.vibrato.tasks.resources.CollectEggsTask;
import dev.vibrato.tasks.resources.CollectMilkTask;
import dev.vibrato.tasks.resources.CollectWoolTask;
import dev.vibrato.tasksystem.Task;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Owned by workstream T2 (see PLAN.md). Register this domain's activities here. */
public final class LifeActivities {

    // Names only: Minecraft objects are resolved inside the factories, so registering never bootstraps the game.
    private static final String[] CROPS = {"wheat", "carrot", "potato", "beetroot"};
    private static final String[] ANIMALS = {"cow", "sheep", "pig", "chicken"};
    private static final String[] RAW_FOODS = {"beef", "porkchop", "mutton", "chicken", "cod", "potato"};

    private LifeActivities() {}

    public static void register(ActivityRegistry registry) {
        add(registry, "sleep_through_night", FeatureArea.SLEEP, facts("has:bed", "loc:surface"), facts(),
                List.of(ParamSpec.choice("spawn_set", "yes", "no")),
                values -> Duration.ofMinutes(12),
                (mod, values) -> new SleepInBedTask(values.getString("spawn_set").equals("yes")));

        add(registry, "night_shelter", FeatureArea.SLEEP, facts(), facts("shelter:built"),
                List.of(),
                values -> Duration.ofMinutes(15),
                (mod, values) -> new NightShelterTask());

        add(registry, "set_spawn_point", FeatureArea.SLEEP, facts("loc:surface"), facts("spawn:set", "home:set"),
                List.of(),
                values -> Duration.ofMinutes(6),
                (mod, values) -> new PlaceBedAndSetSpawnTask());

        add(registry, "build_bed", FeatureArea.CRAFT, facts("has:wool"), facts("has:bed"),
                List.of(),
                values -> Duration.ofMinutes(8),
                (mod, values) -> TaskCatalogue.getItemTask("bed", 1));

        add(registry, "farm_crop", FeatureArea.FARM, facts("loc:surface"), facts("farm:built"),
                List.of(ParamSpec.choice("crop", CROPS), ParamSpec.ints("rows", 2, 8)),
                values -> Duration.ofMinutes(20),
                (mod, values) -> new FarmCropsTask(cropItem(values.getString("crop")), values.getInt("rows")));

        add(registry, "breed_animals", FeatureArea.FARM, facts("loc:surface"), facts(),
                List.of(ParamSpec.choice("animal", ANIMALS), ParamSpec.ints("pairs", 1, 3)),
                values -> Duration.ofMinutes(10),
                (mod, values) -> new BreedAnimalsTask(animalType(values.getString("animal")), values.getInt("pairs")));

        add(registry, "fish", FeatureArea.GATHER, facts("has:fishing_rod", "loc:surface"), facts("food:stocked"),
                List.of(ParamSpec.ints("count", 2, 16)),
                values -> Duration.ofMinutes(Math.min(20, 3 + values.getInt("count"))),
                (mod, values) -> new FishingTask(values.getInt("count")));

        add(registry, "craft_fishing_rod", FeatureArea.CRAFT, facts("has:string"), facts("has:fishing_rod"),
                List.of(), values -> Duration.ofMinutes(4),
                (mod, values) -> TaskCatalogue.getItemTask("fishing_rod", 1));

        add(registry, "cook_food", FeatureArea.CRAFT, facts(), facts("food:stocked"),
                List.of(ParamSpec.choice("raw", RAW_FOODS), ParamSpec.ints("count", 4, 32)),
                values -> Duration.ofMinutes(Math.min(20, 4 + values.getInt("count") / 4)),
                (mod, values) -> new CookFoodTask(rawFood(values.getString("raw")), values.getInt("count")));

        add(registry, "gain_xp_levels", FeatureArea.LEVEL, facts(), facts(),
                List.of(ParamSpec.ints("target", 5, 35)),
                values -> Duration.ofMinutes(15),
                (mod, values) -> new GainXpLevelsTask(values.getInt("target")));

        add(registry, "level_10", FeatureArea.LEVEL, facts(), facts("level:10"),
                List.of(),
                values -> Duration.ofMinutes(15),
                (mod, values) -> new GainXpLevelsTask(10));

        add(registry, "level_20", FeatureArea.LEVEL, facts("level:10"), facts("level:20"),
                List.of(),
                values -> Duration.ofMinutes(15),
                (mod, values) -> new GainXpLevelsTask(20));

        add(registry, "level_30", FeatureArea.LEVEL, facts("level:20"), facts("level:30"),
                List.of(),
                values -> Duration.ofMinutes(15),
                (mod, values) -> new GainXpLevelsTask(30));

        add(registry, "harvest_sugar_cane", FeatureArea.FARM, facts("loc:surface"), facts(),
                List.of(ParamSpec.ints("count", 8, 48)),
                values -> Duration.ofMinutes(10),
                (mod, values) -> new HarvestAndReplantTask(Items.SUGAR_CANE, values.getInt("count")));

        add(registry, "harvest_pumpkin", FeatureArea.FARM, facts("loc:surface"), facts(),
                List.of(ParamSpec.ints("count", 4, 24)),
                values -> Duration.ofMinutes(10),
                (mod, values) -> new HarvestAndReplantTask(Items.PUMPKIN, values.getInt("count")));

        add(registry, "harvest_melon", FeatureArea.FARM, facts("loc:surface"), facts(),
                List.of(ParamSpec.ints("count", 8, 48)),
                values -> Duration.ofMinutes(10),
                (mod, values) -> new HarvestAndReplantTask(Items.MELON_SLICE, values.getInt("count")));

        add(registry, "shear_sheep", FeatureArea.GATHER, facts("loc:surface"), facts("has:wool"),
                List.of(ParamSpec.ints("count", 1, 6)),
                values -> Duration.ofMinutes(6),
                (mod, values) -> new CollectWoolTask(values.getInt("count")));

        add(registry, "collect_milk", FeatureArea.GATHER, facts("loc:surface"), facts(),
                List.of(ParamSpec.ints("count", 1, 6)),
                values -> Duration.ofMinutes(5),
                (mod, values) -> new CollectMilkTask(values.getInt("count")));

        add(registry, "get_eggs", FeatureArea.GATHER, facts("loc:surface"), facts(),
                List.of(ParamSpec.ints("count", 4, 16)),
                values -> Duration.ofMinutes(5),
                (mod, values) -> new CollectEggsTask(values.getInt("count")));
    }

    private static Item cropItem(String name) {
        return switch (name) {
            case "wheat" -> Items.WHEAT;
            case "carrot" -> Items.CARROT;
            case "potato" -> Items.POTATO;
            default -> Items.BEETROOT;
        };
    }

    private static EntityType<?> animalType(String name) {
        return switch (name) {
            case "cow" -> EntityType.COW;
            case "sheep" -> EntityType.SHEEP;
            case "pig" -> EntityType.PIG;
            default -> EntityType.CHICKEN;
        };
    }

    private static Item rawFood(String name) {
        return switch (name) {
            case "beef" -> Items.BEEF;
            case "porkchop" -> Items.PORKCHOP;
            case "mutton" -> Items.MUTTON;
            case "chicken" -> Items.CHICKEN;
            case "cod" -> Items.COD;
            default -> Items.POTATO;
        };
    }

    private static void add(ActivityRegistry registry, String id, FeatureArea area, Set<Fact> requires, Set<Fact> provides,
                            List<ParamSpec> params, Function<ParamValues, Duration> budget,
                            BiFunction<Vibrato, ParamValues, Task> factory) {
        registry.register(new LifeActivity(id, area, requires, provides, params, budget, factory));
    }

    private static Set<Fact> facts(String... keys) {
        return Arrays.stream(keys).map(Fact::of).collect(Collectors.toUnmodifiableSet());
    }

    private record LifeActivity(String id, FeatureArea area, Set<Fact> requires, Set<Fact> provides, List<ParamSpec> params,
                                Function<ParamValues, Duration> timeLimit,
                                BiFunction<Vibrato, ParamValues, Task> factory) implements Activity {

        @Override
        public Task create(Vibrato mod, ParamValues values) {
            return factory.apply(mod, values);
        }

        @Override
        public Duration budget(ParamValues values) {
            return timeLimit.apply(values);
        }
    }
}
