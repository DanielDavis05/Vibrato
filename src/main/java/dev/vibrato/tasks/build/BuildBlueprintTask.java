package dev.vibrato.tasks.build;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.InteractWithBlockTask;
import dev.vibrato.tasks.construction.PlaceBlockTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Raises a {@link Blueprint} at an origin (the ground block under it). Gathers missing materials, then places each
 * block bottom-up, skipping blocks already in place and giving up on a block after a few failed attempts.
 * <p>
 * Water channels are not placed: no bucket placement is implemented, so {@link BlockKind#WATER} entries are left out.
 * Farmland and paths are made by tilling or flattening with a hoe or shovel on the ground block.
 */
public class BuildBlueprintTask extends Task {

    private static final int MAX_ATTEMPTS_PER_BLOCK = 3;
    private static final int MAX_GATHER_ROUNDS = 12;

    private final Blueprint _blueprint;
    private final BlockPos _requestedOrigin;
    private final Palette _palette;

    private Blueprint _oriented;
    private BlockPos _origin;
    private FindBuildSiteTask _siteTask;
    private int[] _attempts;
    private int _gatherRounds;
    private Task _current;
    private int _currentIndex = -1;
    private boolean _done;
    private boolean _ready;

    /** @param origin ground block to build on, or null to pick a flat site automatically */
    public BuildBlueprintTask(Blueprint blueprint, BlockPos origin, Palette palette) {
        _blueprint = blueprint;
        _requestedOrigin = origin;
        _palette = palette;
    }

    public Optional<BlockPos> origin() {
        return Optional.ofNullable(_origin);
    }

    /** True once every placement is in place. False if the task gave up on some block or on gathering. */
    public boolean isReady() {
        return _ready;
    }

    /** Hook for subclasses: the ground block to build on, when it is decided by the caller. */
    protected Optional<BlockPos> anchor(Vibrato mod) {
        return Optional.ofNullable(_requestedOrigin);
    }

    /** Hook for subclasses: the blueprint turned to suit the player, before placement starts. */
    protected Blueprint orient(Vibrato mod, Blueprint blueprint) {
        return blueprint;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _origin = null;
        _siteTask = null;
        _oriented = null;
        _gatherRounds = 0;
        _current = null;
        _currentIndex = -1;
        _done = false;
        _ready = false;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_origin == null) {
            Optional<BlockPos> anchored = anchor(mod);
            if (anchored.isPresent()) {
                _origin = anchored.get();
            } else {
                if (_siteTask == null) {
                    _siteTask = new FindBuildSiteTask(_blueprint.bounds().sizeX(), _blueprint.bounds().sizeZ());
                }
                if (!_siteTask.isFinished(mod)) {
                    return _siteTask;
                }
                Optional<BlockPos> site = _siteTask.site();
                if (site.isEmpty()) {
                    setDebugState("No flat site found");
                    _done = true;
                    return null;
                }
                _origin = site.get();
            }
            _oriented = withoutFluids(orient(mod, _blueprint));
            _attempts = new int[_oriented.placements().size()];
        }

        if (_current != null && _current.isActive() && !_current.isFinished(mod)) {
            return _current;
        }
        if (_current != null && _currentIndex >= 0 && !_current.isFinished(mod)) {
            _attempts[_currentIndex]++;
        }
        _current = null;
        _currentIndex = -1;

        Map<Item, Integer> missing = missingItems(mod);
        if (!missing.isEmpty()) {
            if (++_gatherRounds > MAX_GATHER_ROUNDS) {
                setDebugState("Gave up gathering materials");
                _done = true;
                return null;
            }
            Map.Entry<Item, Integer> first = missing.entrySet().iterator().next();
            setDebugState("Gathering materials");
            _current = TaskCatalogue.getItemTask(first.getKey(), first.getValue());
            return _current;
        }

        for (int i = 0; i < _attempts.length; i++) {
            Blueprint.Placement placement = _oriented.placements().get(i);
            if (_attempts[i] >= MAX_ATTEMPTS_PER_BLOCK || isBuilt(mod, placement)) {
                continue;
            }
            BlockPos pos = _origin.add(placement.dx(), placement.dy(), placement.dz());
            setDebugState("Placing " + placement.kind() + " at " + pos.toShortString());
            _currentIndex = i;
            _current = placeTask(placement, pos);
            return _current;
        }

        _ready = unbuiltCount(mod) == 0;
        _done = true;
        return null;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _done;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof BuildBlueprintTask task && task._blueprint.equals(_blueprint)
                && task._requestedOrigin == _requestedOrigin && task._palette == _palette;
    }

    @Override
    protected String toDebugString() {
        return "Building " + _blueprint.name() + " with " + _palette;
    }

    private static Blueprint withoutFluids(Blueprint blueprint) {
        return new Blueprint(blueprint.name(), blueprint.placements().stream()
                .filter(p -> p.kind() != BlockKind.WATER)
                .toList());
    }

    private Task placeTask(Blueprint.Placement placement, BlockPos pos) {
        if (placement.kind() == BlockKind.FARMLAND || placement.kind() == BlockKind.PATH) {
            Item tool = placement.kind() == BlockKind.FARMLAND ? Items.WOODEN_HOE : Items.WOODEN_SHOVEL;
            return new InteractWithBlockTask(new ItemTarget(tool, 1), Direction.UP, pos, true);
        }
        return new PlaceBlockTask(pos, blockOf(placement.kind()));
    }

    private boolean isBuilt(Vibrato mod, Blueprint.Placement placement) {
        BlockPos pos = _origin.add(placement.dx(), placement.dy(), placement.dz());
        return mod.getWorld().getBlockState(pos).getBlock() == blockOf(placement.kind());
    }

    private int unbuiltCount(Vibrato mod) {
        int count = 0;
        for (Blueprint.Placement placement : _oriented.placements()) {
            if (!isBuilt(mod, placement)) {
                count++;
            }
        }
        return count;
    }

    /** Items still needed for unbuilt placements, in build order, minus what the inventory already holds. */
    private Map<Item, Integer> missingItems(Vibrato mod) {
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (int i = 0; i < _oriented.placements().size(); i++) {
            Blueprint.Placement placement = _oriented.placements().get(i);
            if (_attempts[i] >= MAX_ATTEMPTS_PER_BLOCK || isBuilt(mod, placement)) {
                continue;
            }
            if (placement.kind() == BlockKind.FARMLAND) {
                need.putIfAbsent(Items.WOODEN_HOE, 1);
            } else if (placement.kind() == BlockKind.PATH) {
                need.putIfAbsent(Items.WOODEN_SHOVEL, 1);
            } else {
                need.merge(itemOf(placement.kind()), 1, Integer::sum);
            }
        }
        Map<Item, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> entry : need.entrySet()) {
            int have = mod.getItemStorage().getItemCount(entry.getKey());
            if (have < entry.getValue()) {
                missing.put(entry.getKey(), entry.getValue() - have);
            }
        }
        return missing;
    }

    private Item itemOf(BlockKind kind) {
        String id = _palette.itemId(kind);
        Item item = Registries.ITEM.get(Identifier.of("minecraft", id));
        if (item == Items.AIR) {
            throw new IllegalStateException("No item " + id + " for " + kind);
        }
        return item;
    }

    private Block blockOf(BlockKind kind) {
        if (kind == BlockKind.FARMLAND) {
            return Blocks.FARMLAND;
        }
        if (kind == BlockKind.PATH) {
            return Blocks.DIRT_PATH;
        }
        return Block.getBlockFromItem(itemOf(kind));
    }
}
