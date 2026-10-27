package dev.vibrato.director;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.explore.ExplorableBiomes;
import dev.vibrato.tasks.explore.ExplorableStructures;
import dev.vibrato.tasks.explore.ExploreBiomeTask;
import dev.vibrato.tasks.explore.FindStructureTask;
import dev.vibrato.tasks.explore.FindVillageTask;
import dev.vibrato.tasks.explore.ReturnToPointTask;
import dev.vibrato.tasks.movement.GetToXZTask;
import dev.vibrato.tasks.movement.GetToYTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasks.travel.BoatTravelTask;
import dev.vibrato.tasks.travel.CompassDirection;
import dev.vibrato.tasks.travel.ElytraGlideTask;
import dev.vibrato.tasks.travel.MinecartRideTask;
import dev.vibrato.tasks.travel.NetherExploreTask;
import dev.vibrato.tasks.travel.PortalTravelTask;
import dev.vibrato.tasks.travel.RideAnimalTask;
import dev.vibrato.tasks.travel.WaterCrossingPlanner;
import dev.vibrato.tasks.travel.WaterCrossingPlanner.Crossing;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.Dimension;
import dev.vibrato.util.helpers.ItemHelper;
import dev.vibrato.util.helpers.StorageHelper;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Transport and exploration activities (workstream T3). Every id is prefixed {@code travel_} so it cannot collide with
 * another domain. Factories only build tasks from values their parameters can produce; every offered choice is
 * covered by a tested table (see {@code TravelActivitiesTest}).
 */
public final class TravelActivities {

    private static final Fact SURFACE = Fact.of("loc:surface");
    private static final int DISTANCE_MIN = 40;
    private static final int DISTANCE_MAX = 300;
    private static final List<String> DIRECTIONS = Arrays.stream(CompassDirection.values())
            .map(Enum::name).toList();
    private static final List<String> STRUCTURES = ExplorableStructures.ids();

    private TravelActivities() {}

    public static void register(ActivityRegistry registry) {
        registry.register(activity("travel_boat_cross", Set.of(SURFACE), Set.of(Fact.has("boat")),
                List.of(distance(), direction()), Duration.ofMinutes(12), (mod, v) -> {
                    BlockPos target = target(mod, v);
                    int water = WaterCrossingPlanner.countWaterOnLine(mod.getPlayer().getBlockPos(), target,
                            pos -> mod.getWorld().getFluidState(pos).isOf(Fluids.WATER));
                    boolean hasBoat = mod.getItemStorage().hasItem(ItemHelper.WOOD_BOAT)
                            || StorageHelper.hasCataloguedItem(mod, "planks");
                    Crossing crossing = WaterCrossingPlanner.plan(water, hasBoat,
                            StorageHelper.getBuildingMaterialCount(mod));
                    return crossing == Crossing.BOAT
                            ? new BoatTravelTask(target)
                            : new GetToXZTask(target.getX(), target.getZ());
                }));
        registry.register(activity("travel_ride_horse", Set.of(SURFACE), Set.of(),
                List.of(distance(), direction()), Duration.ofMinutes(10),
                (mod, v) -> new RideAnimalTask("horse", target(mod, v))));
        registry.register(activity("travel_ride_pig", Set.of(SURFACE, Fact.has("saddle")), Set.of(),
                List.of(distance(), direction()), Duration.ofMinutes(8),
                (mod, v) -> new RideAnimalTask("pig", target(mod, v))));
        registry.register(activity("travel_minecart_ride", Set.of(SURFACE), Set.of(),
                List.of(distance(), direction()), Duration.ofMinutes(8),
                (mod, v) -> new MinecartRideTask(target(mod, v))));
        registry.register(activity("travel_elytra_glide", Set.of(SURFACE, Fact.has("elytra")), Set.of(),
                List.of(distance(), direction()), Duration.ofMinutes(6),
                (mod, v) -> new ElytraGlideTask(target(mod, v))));
        registry.register(activity("travel_portal_to_nether", Set.of(Fact.dimension("overworld")),
                Set.of(Fact.dimension("nether"), Fact.of("portal:built")), List.of(), Duration.ofMinutes(15),
                (mod, v) -> new PortalTravelTask(Dimension.NETHER)));
        registry.register(activity("travel_portal_to_overworld", Set.of(Fact.dimension("nether")),
                Set.of(Fact.dimension("overworld")), List.of(), Duration.ofMinutes(15),
                (mod, v) -> new PortalTravelTask(Dimension.OVERWORLD)));
        registry.register(activity("travel_explore_biome", Set.of(SURFACE), Set.of(),
                List.of(ParamSpec.choice("biome", ExplorableBiomes.ids().toArray(String[]::new))),
                Duration.ofMinutes(15), (mod, v) -> new ExploreBiomeTask(v.getString("biome"))));
        registry.register(activity("travel_find_village", Set.of(SURFACE), Set.of(Fact.of("village:found")),
                List.of(), Duration.ofMinutes(12), (mod, v) -> new FindVillageTask()));
        for (String structure : STRUCTURES) {
            // The stronghold is only found with an eye of ender, which craft_ender_eye can make.
            Set<Fact> requires = "stronghold".equals(structure) ? Set.of(SURFACE, Fact.has("ender_eye")) : Set.of(SURFACE);
            registry.register(activity("travel_find_" + structure, requires,
                    Set.of(Fact.of("structure:" + structure + ":found")), List.of(),
                    Duration.ofMinutes("stronghold".equals(structure) ? 25 : 10),
                    (mod, v) -> new FindStructureTask(structure)));
        }
        registry.register(activity("travel_nether_fortress_search", Set.of(Fact.dimension("nether")),
                Set.of(Fact.of("structure:fortress:found")), List.of(), Duration.ofMinutes(20),
                (mod, v) -> new NetherExploreTask(NetherExploreTask.NetherTarget.FORTRESS, 0)));
        registry.register(activity("travel_nether_ancient_debris", Set.of(Fact.dimension("nether"), Fact.has("diamond_pickaxe")),
                Set.of(Fact.has("ancient_debris")), List.of(ParamSpec.ints("debris_y", 8, 20)), Duration.ofMinutes(20),
                (mod, v) -> new NetherExploreTask(NetherExploreTask.NetherTarget.ANCIENT_DEBRIS, v.getInt("debris_y"))));
        registry.register(activity("travel_wander_exploration", Set.of(SURFACE), Set.of(),
                List.of(ParamSpec.ints("radius", 100, 600)), Duration.ofMinutes(8),
                (mod, v) -> new TimeoutWanderTask((float) v.getInt("radius"), true)));
        registry.register(activity("travel_return_to_spawn", Set.of(Fact.of("spawn:set")), Set.of(), List.of(),
                Duration.ofMinutes(6), (mod, v) -> new ReturnToPointTask("spawn")));
        registry.register(activity("travel_return_to_home", Set.of(Fact.of("home:set")), Set.of(), List.of(),
                Duration.ofMinutes(6), (mod, v) -> new ReturnToPointTask("home")));
        registry.register(activity("travel_return_to_village", Set.of(Fact.of("village:found")), Set.of(), List.of(),
                Duration.ofMinutes(6), (mod, v) -> new ReturnToPointTask("village")));
        registry.register(activity("travel_climb_mountain_peak", Set.of(SURFACE), Set.of(),
                List.of(ParamSpec.ints("peak_y", 100, 200)), Duration.ofMinutes(6),
                (mod, v) -> new GetToYTask(v.getInt("peak_y"))));
    }

    private static ParamSpec distance() {
        return ParamSpec.ints("distance", DISTANCE_MIN, DISTANCE_MAX);
    }

    private static ParamSpec direction() {
        return ParamSpec.choice("direction", DIRECTIONS.toArray(String[]::new));
    }

    /** The point the sampled direction and distance lead to from where the bot stands. */
    private static BlockPos target(Vibrato mod, ParamValues values) {
        BlockPos from = mod.getPlayer().getBlockPos();
        String name = values.getString("direction");
        CompassDirection chosen = CompassDirection.N;
        for (CompassDirection candidate : CompassDirection.values()) {
            if (candidate.name().equals(name)) {
                chosen = candidate;
            }
        }
        return chosen.offset(from, values.getInt("distance"));
    }

    private static Activity activity(String id, Set<Fact> requires, Set<Fact> provides, List<ParamSpec> params,
                                     Duration budget, Factory factory) {
        return new TravelActivity(id, requires, provides, params, budget, factory);
    }

    @FunctionalInterface
    private interface Factory {
        Task create(Vibrato mod, ParamValues values);
    }

    private static final class TravelActivity implements Activity {

        private final String id;
        private final Set<Fact> requires;
        private final Set<Fact> provides;
        private final List<ParamSpec> params;
        private final Duration budget;
        private final Factory factory;

        private TravelActivity(String id, Set<Fact> requires, Set<Fact> provides, List<ParamSpec> params,
                               Duration budget, Factory factory) {
            this.id = id;
            this.requires = requires;
            this.provides = provides;
            this.params = params;
            this.budget = budget;
            this.factory = factory;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public FeatureArea area() {
            return id.contains("explore") || id.contains("find") || id.contains("nether_") || id.contains("wander")
                    ? FeatureArea.EXPLORE : FeatureArea.TRANSPORT;
        }

        @Override
        public Set<Fact> requires() {
            return requires;
        }

        @Override
        public Set<Fact> provides() {
            return provides;
        }

        @Override
        public List<ParamSpec> params() {
            return params;
        }

        @Override
        public Task create(Vibrato mod, ParamValues values) {
            return factory.create(mod, values);
        }

        @Override
        public Duration budget(ParamValues values) {
            return budget;
        }
    }
}
