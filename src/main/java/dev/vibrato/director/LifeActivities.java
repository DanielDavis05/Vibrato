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
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Owned by workstream T2 (see PLAN.md). Register this domain's activities here. */
public final class LifeActivities {

    private static final Map<String, Item> CROPS = Map.of(
            "wheat", Items.WHEAT, "carrot", Items.CARROT, "potato", Items.POTATO, "beetroot", Items.BEETROOT);
    private static final Map<String, EntityType<?>> ANIMALS = Map.of(
            "cow", EntityType.COW, "sheep", EntityType.SHEEP, "pig", EntityType.PIG, "chicken", EntityType.CHICKEN);
    private static final Map<String, Item> RAW_FOODS = Map.of(
            "beef", Items.BEEF, "porkchop", Items.PORKCHOP, "mutton", Items.MUTTON,
            "chicken", Items.CHICKEN, "cod", Items.COD, "potato", Items.POTATO);

    private LifeActivities() {}

    public static void register(ActivityRegistry registry) {
        add(registry, "sleep_through_night", FeatureArea.SLEEP, facts("has:bed"), facts(),
                List.of(ParamSpec.choice("spawn_set", "yes", "no")),
                values -> Duration.ofMinutes(12),
                (mod, values) -> new SleepInBedTask(values.getString("spawn_set").equals("yes")));

        add(registry, "night_shelter", FeatureArea.SLEEP, facts(), facts("shelter:built"),
                List.of(),
                values -> Duration.ofMinutes(15),
                (mod, values) -> new NightShelterTask());

        add(registry, "set_spawn_point", FeatureArea.SLEEP, facts(), facts("spawn:set", "home:set"),
                List.of(),
                values -> Duration.ofMinutes(6),
                (mod, values) -> new PlaceBedAndSetSpawnTask());

        add(registry, "build_bed", FeatureArea.CRAFT, facts(), facts("has:bed"),
                List.of(),
                values -> Duration.ofMinutes(8),
                (mod, values) -> TaskCatalogue.getItemTask("bed", 1));

        add(registry, "farm_crop", FeatureArea.FARM, facts(), facts("farm:built"),
                List.of(ParamSpec.choice("crop", CROPS.keySet().toArray(String[]::new)), ParamSpec.ints("rows", 2, 8)),
                values -> Duration.ofMinutes(20),
                (mod, values) -> new FarmCropsTask(CROPS.get(values.getString("crop")), values.getInt("rows")));

        add(registry, "breed_animals", FeatureArea.FARM, facts(), facts(),
                List.of(ParamSpec.choice("animal", ANIMALS.keySet().toArray(String[]::new)), ParamSpec.ints("pairs", 1, 3)),
                values -> Duration.ofMinutes(10),
                (mod, values) -> new BreedAnimalsTask(ANIMALS.get(values.getString("animal")), values.getInt("pairs")));

        add(registry, "fish", FeatureArea.GATHER, facts(), facts("food:stocked"),
                List.of(ParamSpec.ints("count", 2, 16)),
                values -> Duration.ofMinutes(Math.min(20, 3 + values.getInt("count"))),
                (mod, values) -> new FishingTask(values.getInt("count")));

        add(registry, "cook_food", FeatureArea.CRAFT, facts(), facts("food:stocked"),
                List.of(ParamSpec.choice("raw", RAW_FOODS.keySet().toArray(String[]::new)), ParamSpec.ints("count", 4, 32)),
                values -> Duration.ofMinutes(Math.min(20, 4 + values.getInt("count") / 4)),
                (mod, values) -> new CookFoodTask(RAW_FOODS.get(values.getString("raw")), values.getInt("count")));

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

        add(registry, "harvest_sugar_cane", FeatureArea.FARM, facts(), facts(),
                List.of(ParamSpec.ints("count", 8, 48)),
                values -> Duration.ofMinutes(10),
                (mod, values) -> new HarvestAndReplantTask(Items.SUGAR_CANE, values.getInt("count")));

        add(registry, "harvest_pumpkin", FeatureArea.FARM, facts(), facts(),
                List.of(ParamSpec.ints("count", 4, 24)),
                values -> Duration.ofMinutes(10),
                (mod, values) -> new HarvestAndReplantTask(Items.PUMPKIN, values.getInt("count")));

        add(registry, "harvest_melon", FeatureArea.FARM, facts(), facts(),
                List.of(ParamSpec.ints("count", 8, 48)),
                values -> Duration.ofMinutes(10),
                (mod, values) -> new HarvestAndReplantTask(Items.MELON_SLICE, values.getInt("count")));

        add(registry, "shear_sheep", FeatureArea.GATHER, facts(), facts(),
                List.of(ParamSpec.ints("count", 1, 6)),
                values -> Duration.ofMinutes(6),
                (mod, values) -> new CollectWoolTask(values.getInt("count")));

        add(registry, "collect_milk", FeatureArea.GATHER, facts(), facts(),
                List.of(ParamSpec.ints("count", 1, 6)),
                values -> Duration.ofMinutes(5),
                (mod, values) -> new CollectMilkTask(values.getInt("count")));

        add(registry, "get_eggs", FeatureArea.GATHER, facts(), facts(),
                List.of(ParamSpec.ints("count", 4, 16)),
                values -> Duration.ofMinutes(5),
                (mod, values) -> new CollectEggsTask(values.getInt("count")));
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
