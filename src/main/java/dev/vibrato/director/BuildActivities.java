package dev.vibrato.director;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.build.BuildBlueprintTask;
import dev.vibrato.tasks.build.BuildBridgeTask;
import dev.vibrato.tasks.build.BuildFarmTask;
import dev.vibrato.tasks.build.BuildPenTask;
import dev.vibrato.tasks.build.BuildPortalFrameTask;
import dev.vibrato.tasks.build.BuildShelterTask;
import dev.vibrato.tasks.build.BuildTowerTask;
import dev.vibrato.tasks.build.Blueprints;
import dev.vibrato.tasks.build.Palette;
import dev.vibrato.tasks.organize.LootNearbyChestsTask;
import dev.vibrato.tasks.organize.OrganizeInventoryTask;
import dev.vibrato.tasks.organize.StashLootTask;
import dev.vibrato.tasks.resources.GetBuildingMaterialsTask;
import dev.vibrato.tasksystem.Task;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Owned by workstream T4 (see PLAN.md). Register this domain's activities here. */
public final class BuildActivities {

    private static final String[] PALETTES = Arrays.stream(Palette.values())
            .map(palette -> palette.name().toLowerCase(Locale.ROOT)).toArray(String[]::new);

    private BuildActivities() {}

    public static void register(ActivityRegistry registry) {
        add(registry, "build_hut", FeatureArea.BUILD, facts(), facts("shelter:built"),
                List.of(ParamSpec.ints("width", 3, 6), ParamSpec.ints("depth", 3, 6), ParamSpec.choice("palette", PALETTES)),
                values -> minutes(4 + values.getInt("width") * values.getInt("depth") / 4),
                (mod, values) -> new BuildShelterTask(palette(values), values.getInt("width"), values.getInt("depth")));

        add(registry, "build_house", FeatureArea.BUILD, facts(), facts("shelter:built"),
                List.of(ParamSpec.ints("width", 4, 6), ParamSpec.ints("depth", 4, 6), ParamSpec.ints("floors", 1, 2),
                        ParamSpec.choice("palette", PALETTES)),
                values -> minutes(8 + values.getInt("width") * values.getInt("depth") * values.getInt("floors") / 4),
                (mod, values) -> new BuildBlueprintTask(
                        Blueprints.house(values.getInt("width"), values.getInt("depth"), values.getInt("floors")), null, palette(values)));

        add(registry, "build_tower", FeatureArea.BUILD, facts(), facts(),
                List.of(ParamSpec.ints("height", 6, 24), ParamSpec.ints("radius", 2, 3), ParamSpec.choice("palette", PALETTES)),
                values -> minutes(4 + values.getInt("height") / 2),
                (mod, values) -> new BuildTowerTask(palette(values), values.getInt("radius"), values.getInt("height")));

        add(registry, "build_bridge", FeatureArea.BUILD, facts(), facts(),
                List.of(ParamSpec.ints("length", 8, 64), ParamSpec.ints("width", 1, 3), ParamSpec.choice("palette", PALETTES)),
                values -> minutes(4 + values.getInt("length") / 4),
                (mod, values) -> new BuildBridgeTask(palette(values), values.getInt("length"), values.getInt("width")));

        add(registry, "build_wall", FeatureArea.BUILD, facts(), facts(),
                List.of(ParamSpec.ints("length", 4, 32), ParamSpec.ints("height", 2, 6), ParamSpec.choice("palette", PALETTES)),
                values -> minutes(3 + values.getInt("length") / 4),
                (mod, values) -> new BuildBlueprintTask(
                        Blueprints.wall(values.getInt("length"), values.getInt("height")), null, palette(values)));

        add(registry, "build_farm", FeatureArea.FARM, facts(), facts("farm:built"),
                List.of(ParamSpec.ints("rows", 2, 9)),
                values -> minutes(6 + values.getInt("rows") * 2),
                (mod, values) -> new BuildFarmTask(values.getInt("rows")));

        add(registry, "build_pen", FeatureArea.FARM, facts(), facts(),
                List.of(ParamSpec.ints("width", 4, 10), ParamSpec.ints("depth", 4, 10), ParamSpec.choice("palette", PALETTES)),
                values -> minutes(4 + values.getInt("width") * values.getInt("depth") / 8),
                (mod, values) -> new BuildPenTask(palette(values), values.getInt("width"), values.getInt("depth")));

        add(registry, "build_portal_frame", FeatureArea.BUILD, facts("tool:diamond"), facts("portal:built"),
                List.of(),
                values -> minutes(15),
                (mod, values) -> new BuildPortalFrameTask());

        add(registry, "build_pyramid", FeatureArea.BUILD, facts(), facts(),
                List.of(ParamSpec.ints("base", 5, 13), ParamSpec.choice("palette", PALETTES)),
                values -> minutes(4 + values.getInt("base")),
                (mod, values) -> new BuildBlueprintTask(Blueprints.pyramid(values.getInt("base")), null, palette(values)));

        add(registry, "build_obelisk", FeatureArea.BUILD, facts(), facts(),
                List.of(ParamSpec.ints("height", 5, 20), ParamSpec.choice("palette", PALETTES)),
                values -> minutes(4 + values.getInt("height") / 2),
                (mod, values) -> new BuildBlueprintTask(Blueprints.obelisk(values.getInt("height")), null, palette(values)));

        add(registry, "place_chest_and_stash", FeatureArea.CHEST, facts(), facts("chest:placed"),
                List.of(),
                values -> minutes(5),
                (mod, values) -> new StashLootTask(1));

        add(registry, "stash_valuables", FeatureArea.CHEST, facts(), facts(),
                List.of(ParamSpec.ints("min_items", 1, 24)),
                values -> minutes(3 + values.getInt("min_items") / 2),
                (mod, values) -> new StashLootTask(values.getInt("min_items")));

        add(registry, "loot_chests", FeatureArea.CHEST, facts(), facts(),
                List.of(ParamSpec.ints("radius", 16, 48)),
                values -> minutes(4 + values.getInt("radius") / 4),
                (mod, values) -> new LootNearbyChestsTask(values.getInt("radius")));

        add(registry, "organize_inventory", FeatureArea.INVENTORY, facts(), facts(),
                List.of(),
                values -> minutes(4),
                (mod, values) -> new OrganizeInventoryTask());

        add(registry, "gather_building_blocks", FeatureArea.GATHER, facts(), facts(),
                List.of(ParamSpec.ints("count", 16, 256)),
                values -> minutes(3 + values.getInt("count") / 16),
                (mod, values) -> new GetBuildingMaterialsTask(values.getInt("count")));
    }

    private static Palette palette(ParamValues values) {
        return Palette.valueOf(values.getString("palette").toUpperCase(Locale.ROOT));
    }

    private static Duration minutes(int minutes) {
        return Duration.ofMinutes(Math.max(4, Math.min(25, minutes)));
    }

    private static void add(ActivityRegistry registry, String id, FeatureArea area, Set<Fact> requires, Set<Fact> provides,
                            List<ParamSpec> params, Function<ParamValues, Duration> budget,
                            BiFunction<Vibrato, ParamValues, Task> factory) {
        registry.register(new BuildActivity(id, area, requires, provides, params, budget, factory));
    }

    private static Set<Fact> facts(String... keys) {
        return Arrays.stream(keys).map(Fact::of).collect(Collectors.toUnmodifiableSet());
    }

    private record BuildActivity(String id, FeatureArea area, Set<Fact> requires, Set<Fact> provides, List<ParamSpec> params,
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
