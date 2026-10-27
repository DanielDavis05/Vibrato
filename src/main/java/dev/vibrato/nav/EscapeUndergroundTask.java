package dev.vibrato.nav;

import dev.vibrato.Debug;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.construction.DestroyBlockTask;
import dev.vibrato.tasks.movement.GetToBlockTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.time.TimerGame;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Gets the bot from underground to the surface. The rungs run in order and each is time-boxed and logged:
 * craft a wooden pickaxe if there is none, walk to the surface through whatever Baritone can path, then dig straight up
 * through the blocks above the head when {@link SurfaceProbe} says it is safe.
 */
public class EscapeUndergroundTask extends Task {

    private static final int COLUMN_DEPTH = 24;
    private static final double PICKAXE_SECONDS = 90;
    private static final double WALK_SECONDS = 120;
    private static final double DIG_SECONDS = 240;

    private enum Rung {
        PICKAXE, WALK, DIG, GIVE_UP
    }

    private Rung _rung;
    private TimerGame _rungTimer;
    private Task _sub;
    private BlockPos _digTarget;

    @Override
    protected void onStart(Vibrato mod) {
        _sub = null;
        _digTarget = null;
        setRung(mod, hasPickaxe(mod) ? Rung.WALK : Rung.PICKAXE, "start");
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (isFinished(mod)) {
            return null;
        }
        if (_rungTimer.elapsed()) {
            setRung(mod, next(_rung), "time box for " + _rung + " ran out");
        }
        return switch (_rung) {
            case PICKAXE -> {
                if (hasPickaxe(mod)) {
                    setRung(mod, Rung.WALK, "has a pickaxe");
                    yield null;
                }
                _sub = TaskCatalogue.getItemTask("wooden_pick", 1);
                if (_sub == null) {
                    setRung(mod, Rung.WALK, "cannot craft a pickaxe");
                }
                yield _sub;
            }
            case WALK -> {
                if (_sub == null) {
                    _sub = new GetToBlockTask(surfaceTarget(mod));
                } else if (_sub.isFinished(mod)) {
                    setRung(mod, Rung.DIG, "walked to the surface target but still underground");
                    yield null;
                }
                yield _sub;
            }
            case DIG -> digUp(mod);
            case GIVE_UP -> null;
        };
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return SurfaceAccess.isAtSurface(mod);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof EscapeUndergroundTask;
    }

    @Override
    protected String toDebugString() {
        return "Escaping underground (" + _rung + ")";
    }

    private Task digUp(Vibrato mod) {
        if (_sub != null && _sub.isActive() && !_sub.isFinished(mod)) {
            return _sub;
        }
        List<SurfaceProbe.Cell> column = new ArrayList<>();
        List<BlockPos> positions = new ArrayList<>();
        BlockPos feet = mod.getPlayer().getBlockPos();
        for (int i = 0; i < COLUMN_DEPTH; i++) {
            BlockPos pos = feet.up(2 + i);
            positions.add(pos);
            column.add(classify(mod, pos));
        }
        SurfaceProbe.Verdict verdict = SurfaceProbe.decide(column, hasPickaxe(mod));
        switch (verdict) {
            case OPEN_SKY -> {
                return null;
            }
            case DIG_SOFT, DIG_STONE -> {
                int index = 0;
                while (column.get(index) == SurfaceProbe.Cell.AIR) {
                    index++;
                }
                _digTarget = positions.get(index);
                _sub = new DestroyBlockTask(_digTarget);
                return _sub;
            }
            default -> {
                setRung(mod, Rung.GIVE_UP, "digging up is " + verdict);
                return null;
            }
        }
    }

    private void setRung(Vibrato mod, Rung rung, String reason) {
        if (rung == _rung && _rungTimer != null) {
            return;
        }
        Debug.logInternal("Escape underground: " + _rung + " -> " + rung + " (" + reason + ")");
        _rung = rung;
        _sub = null;
        double seconds = switch (rung) {
            case PICKAXE -> PICKAXE_SECONDS;
            case WALK -> WALK_SECONDS;
            case DIG -> DIG_SECONDS;
            case GIVE_UP -> Double.MAX_VALUE / 4;
        };
        _rungTimer = new TimerGame(seconds);
        _rungTimer.reset();
    }

    private static Rung next(Rung rung) {
        return switch (rung) {
            case PICKAXE -> Rung.WALK;
            case WALK -> Rung.DIG;
            case DIG, GIVE_UP -> Rung.GIVE_UP;
        };
    }

    private static BlockPos surfaceTarget(Vibrato mod) {
        return SurfaceAccess.target(mod);
    }

    private static boolean hasPickaxe(Vibrato mod) {
        return mod.getItemStorage().hasItem(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE,
                Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE);
    }

    private static SurfaceProbe.Cell classify(Vibrato mod, BlockPos pos) {
        BlockState state = mod.getWorld().getBlockState(pos);
        if (state.isAir()) {
            return SurfaceProbe.Cell.AIR;
        }
        if (!state.getFluidState().isEmpty()) {
            return SurfaceProbe.Cell.LIQUID;
        }
        Block block = state.getBlock();
        if (block instanceof FallingBlock) {
            return SurfaceProbe.Cell.FALLING;
        }
        if (state.isOf(Blocks.OBSIDIAN) || state.getHardness(mod.getWorld(), pos) < 0) {
            return SurfaceProbe.Cell.HARD;
        }
        if (state.isIn(BlockTags.SHOVEL_MINEABLE)) {
            return SurfaceProbe.Cell.SOFT;
        }
        if (state.isIn(BlockTags.PICKAXE_MINEABLE)) {
            return SurfaceProbe.Cell.STONE;
        }
        return SurfaceProbe.Cell.SOFT;
    }
}
