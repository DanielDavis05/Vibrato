package dev.vibrato.nav;

import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.construction.DestroyBlockTask;
import dev.vibrato.tasks.movement.GetToBlockTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.time.TimerGame;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** A bounded escape that never starts a wood-dependent tool acquisition. */
public class EscapeUndergroundTask extends Task {
    private static final int COLUMN_DEPTH = 24;
    private enum Rung { WALK, ASCEND, GIVE_UP }
    private Rung _rung;
    private TimerGame _rungTimer;
    private TimerGame _stepTimer;
    private Task _sub;
    private BlockPos _stepOrigin;
    private Integer _surfaceY;
    private BlockPos _startPos;
    private boolean _breaking;
    private boolean _oldBreak, _oldPlace;
    private List<Item> _oldThrowaways;
    private final Set<Item> _protectedAtCreation;

    public EscapeUndergroundTask() {
        this(Set.of());
    }

    private EscapeUndergroundTask(Set<Item> protectedAtCreation) {
        _protectedAtCreation = Set.copyOf(protectedAtCreation);
    }

    /** Capture protection before recovery interrupts and unwinds the current task's behaviour. */
    public static EscapeUndergroundTask forRecovery(Vibrato mod) {
        Set<Item> protectedItems = mod.getClientBaritoneSettings().acceptableThrowawayItems.value.stream()
                .filter(item -> mod.getBehaviour().isProtected(item)).collect(Collectors.toSet());
        return new EscapeUndergroundTask(protectedItems);
    }

    @Override
    protected void onStart(Vibrato mod) {
        if (_rung == null) {
            _startPos = mod.getPlayer().getBlockPos();
            _surfaceY = initialSurfaceY(mod);
            setRung(Rung.WALK, "start; surface height " + _surfaceY);
        }
        _oldBreak = mod.getClientBaritoneSettings().allowBreak.value;
        _oldPlace = mod.getClientBaritoneSettings().allowPlace.value;
        _oldThrowaways = new ArrayList<>(mod.getClientBaritoneSettings().acceptableThrowawayItems.value);
        mod.getClientBaritoneSettings().acceptableThrowawayItems.value = new ArrayList<>(_oldThrowaways.stream()
                .filter(item -> !_protectedAtCreation.contains(item) && !mod.getBehaviour().isProtected(item)).toList());
        configurePathing(mod);
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (isFinished(mod)) return null;
        if (_rungTimer.elapsed()) {
            setRung(_rung == Rung.WALK ? Rung.ASCEND : Rung.GIVE_UP, "time limit");
            configurePathing(mod);
        }
        if (_rung == Rung.GIVE_UP) return null;
        if (_rung == Rung.WALK) {
            if (_sub == null) _sub = new GetToBlockTask(SurfaceAccess.target(mod));
            if (_sub.isFinished(mod)) {
                setRung(Rung.ASCEND, "walk target reached below surface");
                configurePathing(mod);
                return null;
            }
            return _sub;
        }
        return ascend(mod);
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        mod.getClientBaritoneSettings().allowBreak.value = _oldBreak;
        mod.getClientBaritoneSettings().allowPlace.value = _oldPlace;
        if (_oldThrowaways != null) mod.getClientBaritoneSettings().acceptableThrowawayItems.value = _oldThrowaways;
    }

    /** True only when this attempt exhausted its safe options, not when it reached the surface. */
    public boolean hasFailed() { return _rung == Rung.GIVE_UP; }

    @Override
    public boolean isFinished(Vibrato mod) {
        if (hasFailed()) return true;
        if (_surfaceY == null) return false;
        BlockPos feet = mod.getPlayer().getBlockPos();
        boolean grounded = mod.getPlayer().isOnGround();
        boolean exposed = SurfaceAccess.isAtSurface(mod);
        if (SurfaceProbe.escaped(feet.getY(), _surfaceY, grounded, exposed)) return true;
        long dx = (long) feet.getX() - _startPos.getX();
        long dz = (long) feet.getZ() - _startPos.getZ();
        return _rung == Rung.WALK && grounded && exposed && dx * dx + dz * dz >= 16
                && SurfaceProbe.escapedByWalking(feet.getY(), initialSurfaceY(mod), grounded, exposed, dx * dx + dz * dz);
    }
    @Override
    protected boolean isEqual(Task other) { return this == other; }
    @Override
    protected String toDebugString() { return "Escaping underground (" + _rung + ")"; }

    private Task ascend(Vibrato mod) {
        BlockPos feet = mod.getPlayer().getBlockPos();
        if (_sub != null) {
            if (_stepTimer.elapsed()) {
                setRung(Rung.GIVE_UP, "no progress on ascent step");
                return null;
            }
            if (_breaking) {
                List<SurfaceProbe.Cell> remaining = new ArrayList<>();
                for (int i = 0; i < COLUMN_DEPTH; i++) remaining.add(classify(mod, _stepOrigin.up(2 + i)));
                if (SurfaceProbe.ascent(remaining, hasPickaxe(mod), adjacentLiquid(mod, _stepOrigin.up(2)))
                        == SurfaceProbe.Ascent.STOP) {
                    setRung(Rung.GIVE_UP, "ascent became unsafe");
                    return null;
                }
                if (!mod.getWorld().getBlockState(_stepOrigin.up(2)).isAir()) return _sub;
            } else if (!SurfaceProbe.landedAbove(feet.getY(), _stepOrigin.getY(), mod.getPlayer().isOnGround())) {
                return _sub;
            }
            _sub = null;
        }
        List<SurfaceProbe.Cell> column = new ArrayList<>();
        for (int i = 0; i < COLUMN_DEPTH; i++) column.add(classify(mod, feet.up(2 + i)));
        SurfaceProbe.Ascent action = SurfaceProbe.ascent(column, hasPickaxe(mod), adjacentLiquid(mod, feet.up(2)));
        if (action == SurfaceProbe.Ascent.STOP) {
            setRung(Rung.GIVE_UP, "unsafe or blocked ascent");
            return null;
        }
        _stepOrigin = feet;
        _stepTimer = new TimerGame(20);
        _stepTimer.reset();
        // Only break a reachable block directly above the head. An open shaft still needs a physical ascent.
        _breaking = action == SurfaceProbe.Ascent.DIG;
        _sub = _breaking ? new DestroyBlockTask(feet.up(2)) : new GetToBlockTask(feet.up());
        return _sub;
    }

    private void setRung(Rung rung, String reason) {
        Debug.logInternal("Escape underground: " + _rung + " -> " + rung + " (" + reason + ")");
        _rung = rung;
        _sub = null;
        _rungTimer = new TimerGame(rung == Rung.WALK ? 30 : 240);
        _rungTimer.reset();
    }

    private void configurePathing(Vibrato mod) {
        // Existing paths first; ascent only places blocks after the ceiling has been checked and cleared.
        mod.getClientBaritoneSettings().allowBreak.value = false;
        mod.getClientBaritoneSettings().allowPlace.value = _rung == Rung.ASCEND && _oldPlace;
    }

    /** Preserve the initial roof height even after digging; use neighboring terrain for an open shaft. */
    private static int initialSurfaceY(Vibrato mod) {
        BlockPos feet = mod.getPlayer().getBlockPos();
        int local = SurfaceAccess.target(mod).getY();
        if (local > feet.getY() + 1) return local;
        if (SurfaceAccess.isAtSurface(mod)) local = feet.getY();
        List<Integer> heights = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos column = feet.add(dx, 0, dz);
                if (!mod.getChunkTracker().isChunkLoaded(column)) continue;
                int top = mod.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, column.getX(), column.getZ());
                int terrain = SurfaceColumn.targetY(feet.getY(), top, y -> {
                    BlockState state = mod.getWorld().getBlockState(new BlockPos(column.getX(), y, column.getZ()));
                    return state.isAir() || isCover(state);
                });
                heights.add(terrain);
            }
        }
        if (heights.size() < 5) return local;
        heights.sort(Integer::compareTo);
        int median = heights.get(heights.size() / 2);
        // Small changes in terrain height should not turn an ordinary depression into a cave.
        return median >= feet.getY() + 4 ? Math.max(local, median) : local;
    }

    private static boolean isCover(BlockState state) {
        return state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS)
                || state.isIn(BlockTags.PLANKS) || state.isIn(BlockTags.WOODEN_SLABS)
                || state.isIn(BlockTags.WOODEN_STAIRS);
    }

    private static boolean adjacentLiquid(Vibrato mod, BlockPos pos) {
        return !mod.getWorld().getFluidState(pos.north()).isEmpty()
                || !mod.getWorld().getFluidState(pos.south()).isEmpty()
                || !mod.getWorld().getFluidState(pos.east()).isEmpty()
                || !mod.getWorld().getFluidState(pos.west()).isEmpty();
    }

    private static boolean hasPickaxe(Vibrato mod) {
        return mod.getItemStorage().hasItem(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE,
                Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE);
    }

    private static SurfaceProbe.Cell classify(Vibrato mod, BlockPos pos) {
        BlockState state = mod.getWorld().getBlockState(pos);
        if (state.isAir()) return SurfaceProbe.Cell.AIR;
        if (!state.getFluidState().isEmpty()) return SurfaceProbe.Cell.LIQUID;
        if (state.getBlock() instanceof FallingBlock) return SurfaceProbe.Cell.FALLING;
        if (state.isOf(Blocks.OBSIDIAN) || state.getHardness(mod.getWorld(), pos) < 0) return SurfaceProbe.Cell.HARD;
        if (state.isIn(BlockTags.PICKAXE_MINEABLE)) return SurfaceProbe.Cell.STONE;
        return SurfaceProbe.Cell.SOFT;
    }
}
