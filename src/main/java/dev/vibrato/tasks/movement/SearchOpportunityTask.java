package dev.vibrato.tasks.movement;

import baritone.api.utils.input.Input;
import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.LookHelper;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** One immediately reachable harvest, with no pathing or tool acquisition. */
final class SearchOpportunityTask extends Task {
    private final BlockPos pos;
    private enum Kind { BERRY, ORE, GRASS }
    private final Kind kind;
    private boolean done;

    private SearchOpportunityTask(BlockPos pos, Kind kind) { this.pos = pos; this.kind = kind; }

    static SearchOpportunityTask find(Vibrato mod) {
        BlockPos feet = mod.getPlayer().getBlockPos();
        SearchOpportunityTask ore = null;
        SearchOpportunityTask grass = null;
        for (BlockPos p : BlockPos.iterate(feet.add(-3, 0, -3), feet.add(3, 2, 3))) {
            BlockState state = mod.getWorld().getBlockState(p);
            boolean berries = state.isOf(Blocks.SWEET_BERRY_BUSH) && state.get(SweetBerryBushBlock.AGE) >= 2;
            boolean mine = isOre(state) && mod.getPlayer().getMainHandStack().getItem() instanceof PickaxeItem
                    && mod.getPlayer().getMainHandStack().isSuitableFor(state)
                    && WorldHelper.canBreak(mod, p) && safeOre(mod, p);
            boolean seeds = state.isOf(Blocks.SHORT_GRASS) && !mod.getItemStorage().hasItem(Items.WHEAT_SEEDS)
                    && WorldHelper.canBreak(mod, p);
            if ((berries || mine || seeds) && LookHelper.getReach(p).isPresent()) {
                if (berries) return new SearchOpportunityTask(p.toImmutable(), Kind.BERRY);
                if (mine && ore == null) ore = new SearchOpportunityTask(p.toImmutable(), Kind.ORE);
                if (seeds && grass == null) grass = new SearchOpportunityTask(p.toImmutable(), Kind.GRASS);
            }
        }
        return ore != null ? ore : grass;
    }

    private static boolean isOre(BlockState s) {
        return s.isOf(Blocks.COAL_ORE) || s.isOf(Blocks.DEEPSLATE_COAL_ORE)
                || s.isOf(Blocks.COPPER_ORE) || s.isOf(Blocks.DEEPSLATE_COPPER_ORE)
                || s.isOf(Blocks.IRON_ORE) || s.isOf(Blocks.DEEPSLATE_IRON_ORE)
                || s.isOf(Blocks.GOLD_ORE) || s.isOf(Blocks.DEEPSLATE_GOLD_ORE)
                || s.isOf(Blocks.REDSTONE_ORE) || s.isOf(Blocks.DEEPSLATE_REDSTONE_ORE)
                || s.isOf(Blocks.LAPIS_ORE) || s.isOf(Blocks.DEEPSLATE_LAPIS_ORE)
                || s.isOf(Blocks.DIAMOND_ORE) || s.isOf(Blocks.DEEPSLATE_DIAMOND_ORE)
                || s.isOf(Blocks.EMERALD_ORE) || s.isOf(Blocks.DEEPSLATE_EMERALD_ORE);
    }

    private static boolean safeOre(Vibrato mod, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!mod.getWorld().getFluidState(pos.offset(direction)).isEmpty()) return false;
        }
        return true;
    }

    @Override protected void onStart(Vibrato mod) { done = false; }
    @Override protected Task onTick(Vibrato mod) {
        BlockState state = mod.getWorld().getBlockState(pos);
        boolean valid = switch (kind) {
            case BERRY -> state.isOf(Blocks.SWEET_BERRY_BUSH) && state.get(SweetBerryBushBlock.AGE) >= 2;
            case ORE -> isOre(state) && mod.getPlayer().getMainHandStack().getItem() instanceof PickaxeItem
                    && mod.getPlayer().getMainHandStack().isSuitableFor(state) && safeOre(mod, pos);
            case GRASS -> state.isOf(Blocks.SHORT_GRASS) && !mod.getItemStorage().hasItem(Items.WHEAT_SEEDS);
        };
        if (!valid || (kind != Kind.BERRY && !WorldHelper.canBreak(mod, pos))) {
            done = true;
            return null;
        }
        var reach = LookHelper.getReach(pos);
        if (reach.isEmpty()) { done = true; return null; }
        LookHelper.lookAt(mod, reach.get());
        if (LookHelper.isLookingAt(mod, pos) && LookHelper.isLookingAtNow(mod, pos)) {
            if (kind == Kind.BERRY) {
                mod.getInputControls().tryPress(Input.CLICK_RIGHT);
                done = true;
            } else {
                mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
            }
        } else {
            mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
        }
        return null;
    }
    @Override protected void onStop(Vibrato mod, Task interruptTask) {
        mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
    }
    @Override public boolean isFinished(Vibrato mod) { return done; }
    @Override protected boolean isEqual(Task other) { return other instanceof SearchOpportunityTask task && pos.equals(task.pos) && kind == task.kind; }
    @Override protected String toDebugString() { return (kind == Kind.BERRY ? "Harvest berries at " : kind == Kind.ORE ? "Mine nearby ore at " : "Gather seeds from grass at ") + pos.toShortString(); }
}
