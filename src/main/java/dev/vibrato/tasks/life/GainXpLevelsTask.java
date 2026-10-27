package dev.vibrato.tasks.life;

import dev.vibrato.Vibrato;
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
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Item;

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
    private static final Class<?>[] HOSTILES = {ZombieEntity.class, SkeletonEntity.class, SpiderEntity.class, CreeperEntity.class};

    private final int _targetLevel;
    private final TimerGame _budget = new TimerGame(TIME_LIMIT_SECONDS);
    private SmeltInFurnaceTask _smelt;
    private KillEntitiesTask _kill;

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
        Item raw = null;
        Item cooked = null;
        for (Item candidate : ItemHelper.RAW_FOODS) {
            if (mod.getItemStorage().hasItem(candidate)) {
                raw = candidate;
                cooked = ItemHelper.getCookedFood(candidate).orElse(null);
                break;
            }
        }
        if (raw != null && cooked != null) {
            if (_smelt == null || _smelt.isFinished(mod)) {
                _smelt = new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(cooked, 1), new ItemTarget(raw, 1)));
                _smelt.ignoreMaterials();
            }
            setDebugState("Smelting food for XP");
            return _smelt;
        }

        if (mod.getBlockTracker().anyFound(ORES)) {
            setDebugState("Mining ore for XP");
            return new DoToClosestBlockTask(DestroyBlockTask::new, pos -> WorldHelper.canBreak(mod, pos), ORES);
        }

        if (mod.getEntityTracker().entityFound(HOSTILES)) {
            if (_kill == null) {
                _kill = new KillEntitiesTask(HOSTILES);
            }
            setDebugState("Killing hostile mobs for XP");
            return _kill;
        }

        setDebugState("Searching for XP sources (level " + mod.getPlayer().experienceLevel + ")");
        return new TimeoutWanderTask();
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
