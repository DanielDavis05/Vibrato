package dev.vibrato.tasks.enchant;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.entity.KillEntitiesTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.time.TimerGame;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Items;

/**
 * Gathers experience until the player reaches {@code targetLevel}, by killing hostile mobs (which drop XP orbs) and
 * mining lapis lazuli (which also drops XP). Gives up after a fixed time budget, see {@link #failed()}.
 */
public class CollectXpTask extends Task {

    private static final double BUDGET_SECONDS = 240;
    private static final int LAPIS_BATCH = 4;

    private final int _targetLevel;
    private final TimerGame _budget = new TimerGame(BUDGET_SECONDS);
    private final TimeoutWanderTask _wander = new TimeoutWanderTask();
    private boolean _timedOut = false;

    public CollectXpTask(int targetLevel) {
        _targetLevel = targetLevel;
    }

    /** True when the time budget ran out before the target level was reached. */
    public boolean failed() {
        return _timedOut;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _budget.reset();
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (reachedTarget(mod) || _timedOut) {
            return null;
        }
        if (_budget.elapsed()) {
            _timedOut = true;
            return null;
        }
        if (mod.getEntityTracker().entityFound(HostileEntity.class)) {
            setDebugState("Killing mobs for XP");
            return new KillEntitiesTask(HostileEntity.class);
        }
        setDebugState("Mining lapis for XP");
        int lapis = mod.getItemStorage().getItemCount(Items.LAPIS_LAZULI);
        Task mine = TaskCatalogue.getItemTask(Items.LAPIS_LAZULI, lapis + LAPIS_BATCH);
        return mine != null ? mine : _wander;
    }

    private boolean reachedTarget(Vibrato mod) {
        return mod.getPlayer().experienceLevel >= _targetLevel;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _timedOut || reachedTarget(mod);
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof CollectXpTask task && task._targetLevel == _targetLevel;
    }

    @Override
    protected String toDebugString() {
        return "Collecting XP to level " + _targetLevel;
    }
}
