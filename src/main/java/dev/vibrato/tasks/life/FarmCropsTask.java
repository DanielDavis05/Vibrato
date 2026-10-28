package dev.vibrato.tasks.life;

import dev.vibrato.Vibrato;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.DoToClosestBlockTask;
import dev.vibrato.tasks.InteractWithBlockTask;
import dev.vibrato.tasks.construction.DestroyBlockTask;
import dev.vibrato.tasks.movement.IdleTask;
import dev.vibrato.tasks.movement.GetWithinRangeOfBlockTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasks.resources.MineAndCollectTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.MiningRequirement;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Arrays;
import java.util.Optional;

/**
 * Keeps a small crop field going: tills farmland next to water, plants seeds, waits for growth, harvests only fully
 * grown crops and replants. Finishes once the inventory holds one field's worth of the crop.
 */
public class FarmCropsTask extends Task {

    private static final Item[] HOES = {
            Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.GOLDEN_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE
    };
    private static final Block[] TILLABLE = {Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT};
    private static final int HYDRATION_RANGE = 4;
    private static final int FIELD_CELLS_PER_ROW = 9;

    private final Item _crop;
    private final int _rows;
    private final CropGrowth _growth;
    private final Item _seed;
    private final Block _cropBlock;
    private final FarmSeedPlan _wheatSeeds = new FarmSeedPlan();
    private final FarmFieldScope _field = new FarmFieldScope();

    public FarmCropsTask(CropGrowth growth, int rows) {
        _growth = growth;
        _rows = rows;
        _crop = Registries.ITEM.get(Identifier.of("minecraft", growth.itemId()));
        _seed = Registries.ITEM.get(Identifier.of("minecraft", growth.seedId()));
        _cropBlock = Registries.BLOCK.get(Identifier.of("minecraft", growth.blockId()));
    }

    private int fieldSize() {
        return _rows * FIELD_CELLS_PER_ROW;
    }

    @Override
    protected void onStart(Vibrato mod) {
        mod.getBehaviour().push();
        mod.getBlockTracker().trackBlock(Blocks.FARMLAND, Blocks.WATER, _cropBlock);
    }

    @Override
    protected Task onTick(Vibrato mod) {
        establishNearbyField(mod);
        int wheatHeld = _seed == Items.WHEAT_SEEDS ? mainSeedCount(mod) : 0;
        // Do not infer an unloaded or distant field is empty. A committed seed
        // collector may finish away from it; otherwise return and reload first.
        if (_field.anchor() != null
                && (!inField(mod, mod.getPlayer().getBlockPos())
                    || !mod.getChunkTracker().isChunkLoaded(_field.anchor()))
                && !_wheatSeeds.gathering(wheatHeld)) {
            setDebugState("Returning to the planted field");
            return new GetWithinRangeOfBlockTask(_field.anchor(), 8);
        }
        Optional<BlockPos> ripe = mod.getBlockTracker().getNearestTracking(
                mod.getPlayer().getPos(), pos -> inField(mod, pos) && isMature(mod, pos), _cropBlock);
        if (ripe.isPresent()) {
            setDebugState("Harvesting grown " + _growth.itemId());
            return new DoToClosestBlockTask(DestroyBlockTask::new, pos -> inField(mod, pos) && isMature(mod, pos), _cropBlock);
        }

        if (_seed == Items.WHEAT_SEEDS) {
            // Generic wheat-seed collection chooses existing wheat before grass;
            // an immature crop then stalls its own seed prerequisite. Gather
            // only grass here, with a finite remaining field-size target.
            int goal = _wheatSeeds.seedGoal(fieldSize(), nearbyPlantedCrops(mod), wheatHeld, false);
            if (goal > 0) {
                setDebugState("Collecting wheat seeds for the remaining field");
                return new MineAndCollectTask(_seed, goal,
                        new Block[]{Blocks.SHORT_GRASS, Blocks.TALL_GRASS}, MiningRequirement.HAND);
            }
        } else if (mod.getItemStorage().getItemCount(_seed) < 1
                && (!hasPlantedCrops(mod) || nearbyFarmland(mod) < fieldSize())) {
            setDebugState("Collecting seeds");
            return TaskCatalogue.getItemTask(_seed, fieldSize());
        }

        Optional<BlockPos> emptyFarmland = mod.getBlockTracker().getNearestTracking(
                mod.getPlayer().getPos(), pos -> inField(mod, pos) && isEmptyFarmland(mod, pos), Blocks.FARMLAND);
        if (emptyFarmland.isPresent() && mod.getItemStorage().hasItem(_seed)) {
            setDebugState("Planting " + _growth.seedId());
            return new InteractWithBlockTask(new ItemTarget(_seed, 1), Direction.UP, emptyFarmland.get(), true);
        }

        // Keep preparing a full field while seeds remain. Waiting on the first
        // planted crop leaves the rest of the field empty for an entire growth cycle.
        if (mod.getItemStorage().hasItem(_seed)
                && nearbyFarmland(mod) < fieldSize()) {
            if (!mod.getItemStorage().hasItem(HOES)) {
                setDebugState("Crafting a hoe");
                return TaskCatalogue.getItemTask(Items.WOODEN_HOE, 1);
            }
            Optional<BlockPos> tillable = findTillable(mod);
            if (tillable.isPresent()) {
                _field.establish(tillable.get());
                setDebugState("Tilling farmland next to water");
                return new InteractWithBlockTask(new ItemTarget(HOES, 1), tillable.get(), true);
            }
        }
        if (hasPlantedCrops(mod)) {
            setDebugState("Waiting for crops to grow");
            return new IdleTask();
        }
        setDebugState("Searching for a place to farm");
        return new TimeoutWanderTask();
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        mod.getBehaviour().pop();
        mod.getBlockTracker().stopTracking(Blocks.FARMLAND, Blocks.WATER, _cropBlock);
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return mod.getItemStorage().getItemCount(_crop) >= fieldSize();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof FarmCropsTask task && task._crop == _crop && task._rows == _rows;
    }

    @Override
    protected String toDebugString() {
        return "Farming " + _growth.itemId() + " in " + _rows + " rows";
    }

    private long nearbyFarmland(Vibrato mod) {
        return mod.getBlockTracker().getKnownLocations(Blocks.FARMLAND).stream()
                .filter(pos -> inField(mod, pos))
                .filter(pos -> mod.getWorld().getBlockState(pos).isOf(Blocks.FARMLAND))
                .count();
    }

    private int mainSeedCount(Vibrato mod) {
        return mod.getPlayer().getInventory().main.stream()
                .filter(stack -> stack.isOf(_seed)).mapToInt(stack -> stack.getCount()).sum();
    }

    private boolean inField(Vibrato mod, BlockPos pos) {
        return _field.contains(pos, mod.getPlayer().getBlockPos());
    }

    private void establishNearbyField(Vibrato mod) {
        mod.getBlockTracker().getKnownLocations(Blocks.FARMLAND).stream()
                .filter(pos -> pos.isWithinDistance(mod.getPlayer().getPos(), 12))
                .filter(pos -> {
                    BlockState state = mod.getWorld().getBlockState(pos);
                    return state.isOf(Blocks.FARMLAND) && state.get(FarmlandBlock.MOISTURE) > 0;
                })
                .min(java.util.Comparator.comparingDouble(pos -> pos.getSquaredDistance(mod.getPlayer().getPos())))
                .ifPresent(_field::establish);
    }

    private int nearbyPlantedCrops(Vibrato mod) {
        return (int) mod.getBlockTracker().getKnownLocations(_cropBlock).stream()
                .filter(pos -> inField(mod, pos))
                .filter(pos -> mod.getWorld().getBlockState(pos).isOf(_cropBlock))
                .count();
    }

    private boolean isMature(Vibrato mod, BlockPos pos) {
        BlockState state = mod.getWorld().getBlockState(pos);
        return state.getBlock() instanceof CropBlock crop && crop.isMature(state);
    }

    private boolean hasPlantedCrops(Vibrato mod) {
        return mod.getBlockTracker().getKnownLocations(_cropBlock).stream()
                .anyMatch(pos -> inField(mod, pos) && mod.getWorld().getBlockState(pos).isOf(_cropBlock));
    }

    private boolean isEmptyFarmland(Vibrato mod, BlockPos pos) {
        return mod.getWorld().getBlockState(pos).isOf(Blocks.FARMLAND) && WorldHelper.isAir(mod, pos.up());
    }

    /** A tillable block with air above it, within hydration range of a water source. */
    private Optional<BlockPos> findTillable(Vibrato mod) {
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (BlockPos water : mod.getBlockTracker().getKnownLocations(Blocks.WATER)) {
            for (int dx = -HYDRATION_RANGE; dx <= HYDRATION_RANGE; ++dx) {
                for (int dz = -HYDRATION_RANGE; dz <= HYDRATION_RANGE; ++dz) {
                    BlockPos candidate = water.add(dx, 0, dz);
                    Block block = mod.getWorld().getBlockState(candidate).getBlock();
                    if (!inField(mod, candidate)) continue;
                    if (!Arrays.asList(TILLABLE).contains(block) || !WorldHelper.isAir(mod, candidate.up())) {
                        continue;
                    }
                    double distance = candidate.getSquaredDistance(mod.getPlayer().getPos());
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
