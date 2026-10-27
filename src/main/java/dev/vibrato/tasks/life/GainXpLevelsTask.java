package dev.vibrato.tasks.life;

import dev.vibrato.Vibrato;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.DoToClosestBlockTask;
import dev.vibrato.tasks.container.SmeltInFurnaceTask;
import dev.vibrato.tasks.construction.DestroyBlockTask;
import dev.vibrato.tasks.entity.KillEntitiesTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.SmeltTarget;
import dev.vibrato.util.helpers.ItemHelper;
import dev.vibrato.util.helpers.WorldHelper;
import dev.vibrato.util.time.TimerGame;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.entity.Entity;
import java.util.Arrays;

/**
 * Raises the experience level by the cheapest means at hand, in order: smelt raw ore or food in a furnace, mine
 * coal, lapis, redstone or diamond ore, then kill hostile mobs. Experience orbs are picked up by walking near them,
 * which the other steps do anyway. Bounded by a time limit.
 */
public class GainXpLevelsTask extends Task {

    private static final double TIME_LIMIT_SECONDS = 15 * 60;
    private static final Block[] ORES = {
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE
    };
    private static final Class<?>[] HOSTILES = {ZombieEntity.class, SkeletonEntity.class, SpiderEntity.class};

    private final int _targetLevel;
    private final TimerGame _budget = new TimerGame(TIME_LIMIT_SECONDS);
    private SmeltInFurnaceTask _smelt;
    private KillEntitiesTask _kill;
    private Task _replacementPickaxe;
    private Source _source;

    enum Source { SMELT, ORE, MOBS, SEARCH }

    static final class SourcePolicy {
        static boolean needsReplacement(Source current, boolean hasBasicPickaxe) {
            return current == Source.ORE && !hasBasicPickaxe;
        }

        /** Keep an available source until it finishes or disappears; otherwise choose by cost. */
        static Source choose(Source current, boolean smelt, boolean ore, boolean mobs) {
            if (current == Source.SMELT && smelt || current == Source.ORE && ore || current == Source.MOBS && mobs) {
                return current;
            }
            return smelt ? Source.SMELT : ore ? Source.ORE : mobs ? Source.MOBS : Source.SEARCH;
        }
    }

    public GainXpLevelsTask(int targetLevel) {
        _targetLevel = targetLevel;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _budget.reset();
        mod.getBlockTracker().trackBlock(ORES);
    }

    @Override
    protected Task onTick(Vibrato mod) {
        // Finish a started craft, including moving its result out of the cursor, before resuming XP.
        if (_replacementPickaxe != null) {
            if (!_replacementPickaxe.isFinished(mod)) {
                setDebugState("Replacing broken pickaxe for XP mining");
                return _replacementPickaxe;
            }
            _replacementPickaxe = null;
        }
        boolean hasBasicPickaxe = mod.getItemStorage().getItemCountInventoryOnly(
                Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE) > 0;
        if (SourcePolicy.needsReplacement(_source, hasBasicPickaxe)) {
            _replacementPickaxe = java.util.Objects.requireNonNull(TaskCatalogue.getItemTask("stone_pick", 1));
            setDebugState("Replacing broken pickaxe for XP mining");
            return _replacementPickaxe;
        }

        Item raw = null;
        Item cooked = null;
        for (Item candidate : ItemHelper.RAW_FOODS) {
            if (mod.getItemStorage().hasItem(candidate)) {
                raw = candidate;
                cooked = ItemHelper.getCookedFood(candidate).orElse(null);
                break;
            }
        }
        boolean smeltActive = _source == Source.SMELT && _smelt != null && !_smelt.isFinished(mod);
        Block[] mineable = Arrays.stream(ORES).filter(block -> mod.getItemStorage()
                .getItemStacksPlayerInventory(true).stream().anyMatch(stack -> stack.isSuitableFor(block.getDefaultState())))
                .toArray(Block[]::new);
        boolean oreAvailable = mineable.length > 0 && mod.getBlockTracker()
                .getNearestTracking(mod.getPlayer().getPos(), pos -> WorldHelper.canBreak(mod, pos), mineable).isPresent();
        boolean armed = mod.getItemStorage().hasItem(Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD,
                Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD);
        boolean mobsAvailable = armed && mod.getEntityTracker().getClosestEntity(
                entity -> nearbyHostile(mod, entity), HOSTILES).isPresent();
        _source = SourcePolicy.choose(_source, smeltActive || raw != null && cooked != null, oreAvailable, mobsAvailable);

        if (_source == Source.SMELT) {
            if (_smelt == null || _smelt.isFinished(mod)) {
                int targetCount = mod.getItemStorage().getItemCountInventoryOnly(cooked) + 1;
                _smelt = new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(cooked, targetCount), new ItemTarget(raw, targetCount)));
                _smelt.ignoreMaterials();
            }
            setDebugState("Smelting food for XP");
            return _smelt;
        }

        if (_source == Source.ORE) {
            setDebugState("Mining ore for XP");
            return new DoToClosestBlockTask(DestroyBlockTask::new, pos -> WorldHelper.canBreak(mod, pos), mineable);
        }

        if (_source == Source.MOBS) {
            if (_kill == null) {
                _kill = new KillEntitiesTask(entity -> nearbyHostile(mod, entity), HOSTILES);
            }
            setDebugState("Killing hostile mobs for XP");
            return _kill;
        }

        setDebugState("Searching for XP sources (level " + mod.getPlayer().experienceLevel + ")");
        return new TimeoutWanderTask();
    }

    private static boolean nearbyHostile(Vibrato mod, Entity entity) {
        return entity.isAlive() && mod.getEntityTracker().isEntityReachable(entity)
                && entity.squaredDistanceTo(mod.getPlayer()) <= 12 * 12
                && Math.abs(entity.getY() - mod.getPlayer().getY()) <= 4;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(ORES);
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return mod.getPlayer().experienceLevel >= _targetLevel || _budget.elapsed();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GainXpLevelsTask task && task._targetLevel == _targetLevel;
    }

    @Override
    protected String toDebugString() {
        return "Gaining experience up to level " + _targetLevel + " (" + XpMath.totalXpForLevel(_targetLevel) + " xp total)";
    }
}
