package dev.vibrato.tasks.life;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.construction.DestroyBlockTask;
import dev.vibrato.tasks.construction.PlaceBlockTask;
import dev.vibrato.tasks.movement.GetToBlockTask;
import dev.vibrato.tasks.movement.IdleTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.WorldHelper;
import dev.vibrato.util.time.TimerGame;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Passes the night without a bed. Digs a one-block pit into solid ground with walls on every side, caps it, waits
 * for morning and digs out. If no such ground is nearby, walls in a 3x3 enclosure on the spot instead.
 */
public class NightShelterTask extends Task {

    private static final int SEARCH_RADIUS = 6;
    private static final int SEARCH_DEPTH = 2;
    private static final Block[] DIG_SAFE = {
            Blocks.STONE, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.COBBLESTONE,
            Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE
    };
    private static final Block[] BUILD_BLOCKS = {Blocks.COBBLESTONE, Blocks.DIRT, Blocks.STONE};

    private enum Phase { SEARCH, DIG, ENTER, CAP, WAIT, EXIT, ENCLOSE, DONE }

    private final TimerGame _capTimer = new TimerGame(15);
    private Phase _phase = Phase.SEARCH;
    // Feet cell of the pit (the dug-out block). Null when we are walling in a 3x3 enclosure instead.
    private BlockPos _pit;
    private List<BlockPos> _enclosure = List.of();

    @Override
    protected void onStart(Vibrato mod) {
        _phase = Phase.SEARCH;
        _pit = null;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_phase == Phase.SEARCH) {
            if (!WorldHelper.canSleep()) {
                _phase = Phase.DONE;
                return null;
            }
            Optional<BlockPos> pit = findPit(mod);
            if (pit.isPresent()) {
                _pit = pit.get();
                _phase = Phase.DIG;
            } else {
                _enclosure = enclosureTargets(mod.getPlayer().getBlockPos());
                _phase = Phase.ENCLOSE;
            }
        }

        if (_phase == Phase.DIG) {
            if (WorldHelper.isAir(mod, _pit)) {
                _phase = Phase.ENTER;
            } else {
                setDebugState("Digging a night shelter");
                if (!mod.getPlayer().getBlockPos().equals(_pit.up())) {
                    return new GetToBlockTask(_pit.up());
                }
                return new DestroyBlockTask(_pit);
            }
        }

        if (_phase == Phase.ENTER) {
            if (mod.getPlayer().getBlockPos().equals(_pit)) {
                _capTimer.reset();
                _phase = Phase.CAP;
            } else {
                setDebugState("Going down into the shelter");
                return new GetToBlockTask(_pit);
            }
        }

        if (_phase == Phase.CAP) {
            BlockPos cap = _pit.up(2);
            if (!WorldHelper.isAir(mod, cap) || _capTimer.elapsed()) {
                _phase = Phase.WAIT;
            } else {
                setDebugState("Capping the shelter");
                return new PlaceBlockTask(cap, BUILD_BLOCKS);
            }
        }

        if (_phase == Phase.WAIT) {
            if (WorldHelper.canSleep()) {
                setDebugState("Waiting for morning");
                return new IdleTask();
            }
            _phase = Phase.EXIT;
        }

        if (_phase == Phase.EXIT) {
            if (_pit == null || WorldHelper.isAir(mod, _pit.up(2))) {
                _phase = Phase.DONE;
                return null;
            }
            setDebugState("Morning, opening the shelter");
            return new DestroyBlockTask(_pit.up(2));
        }

        if (_phase == Phase.ENCLOSE) {
            Optional<BlockPos> next = _enclosure.stream().filter(pos -> !WorldHelper.isSolid(mod, pos)).findFirst();
            if (next.isPresent()) {
                setDebugState("Walling in a 3x3 shelter");
                return new PlaceBlockTask(next.get(), BUILD_BLOCKS);
            }
            _phase = Phase.WAIT;
        }

        return null;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _phase == Phase.DONE;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof NightShelterTask;
    }

    @Override
    protected String toDebugString() {
        return "Sheltering for the night (" + _phase.name().toLowerCase() + ")";
    }

    private static Optional<BlockPos> findPit(Vibrato mod) {
        BlockPos origin = mod.getPlayer().getBlockPos();
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; ++dx) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; ++dz) {
                for (int dy = -SEARCH_DEPTH; dy <= SEARCH_DEPTH; ++dy) {
                    BlockPos pit = origin.add(dx, dy, dz);
                    double distance = pit.getSquaredDistance(origin);
                    if (distance < bestDistance && isPitSite(mod, pit)) {
                        best = pit;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** A dug-out cell in diggable ground, with air above it and solid walls around the pit, the air and the cap. */
    private static boolean isPitSite(Vibrato mod, BlockPos pit) {
        Block block = mod.getWorld().getBlockState(pit).getBlock();
        if (!Arrays.asList(DIG_SAFE).contains(block)) return false;
        if (!isSolidGround(mod, pit.down())) return false;
        BlockPos head = pit.up();
        if (!WorldHelper.isAir(mod, head)) return false;
        return isWalled(mod, pit) && isWalled(mod, head) && isWalled(mod, pit.up(2));
    }

    private static boolean isWalled(Vibrato mod, BlockPos pos) {
        for (BlockPos side : new BlockPos[]{pos.north(), pos.south(), pos.east(), pos.west()}) {
            if (!isSolidGround(mod, side)) return false;
        }
        return true;
    }

    private static boolean isSolidGround(Vibrato mod, BlockPos pos) {
        return WorldHelper.isSolid(mod, pos) && !WorldHelper.isFallingBlock(pos);
    }

    /** Two storeys of walls around the centre column and a roof over it, all in a 3x3 footprint. */
    private static List<BlockPos> enclosureTargets(BlockPos centre) {
        List<BlockPos> targets = new ArrayList<>();
        for (int y = 0; y <= 1; ++y) {
            for (int dx = -1; dx <= 1; ++dx) {
                for (int dz = -1; dz <= 1; ++dz) {
                    if (dx != 0 || dz != 0) {
                        targets.add(centre.add(dx, y, dz));
                    }
                }
            }
        }
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                targets.add(centre.add(dx, 2, dz));
            }
        }
        return targets;
    }
}
