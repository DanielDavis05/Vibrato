package dev.vibrato.tasks.explore;

import dev.vibrato.Vibrato;
import dev.vibrato.nav.NavSafety;
import dev.vibrato.tasks.movement.GetToBlockTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.util.math.BlockPos;

/**
 * Walks back to a remembered point: {@code home} (the configured home base), or the nearest {@link DiscoveryLog} entry of
 * that kind (village, structure:&lt;id&gt;, biome:&lt;id&gt;). {@code spawn} is the bed the bot last slept in, read from the
 * persisted waypoints that the bed task records.
 * <p>
 * The target is chosen once when the task starts. If nothing is remembered, the task ends at once.
 */
public class ReturnToPointTask extends Task {

    private static final double ARRIVAL_RANGE = 8;

    private final String _kind;
    private BlockPos _target;

    public ReturnToPointTask(String kind) {
        _kind = kind;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _target = resolve(mod);
    }

    private BlockPos resolve(Vibrato mod) {
        if ("home".equals(_kind)) {
            return mod.getModSettings().getHomeBasePosition();
        }
        if ("spawn".equals(_kind)) {
            String dimension = mod.getWorld().getRegistryKey().getValue().toString();
            return SpawnPointLookup.bedIn(NavSafety.waypoints(), dimension).orElse(null);
        }
        return DiscoveryLog.nearest(_kind, mod.getPlayer().getBlockPos()).orElse(null);
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        _target = null;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_target == null) {
            setDebugState("Nothing remembered for " + _kind);
            return null;
        }
        setDebugState("Returning to " + _kind + " at " + _target.toShortString());
        return new GetToBlockTask(_target);
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _target == null || WorldHelper.inRangeXZ(mod.getPlayer(), _target, ARRIVAL_RANGE);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ReturnToPointTask task && task._kind.equals(_kind);
    }

    @Override
    protected String toDebugString() {
        return "Returning to " + _kind;
    }
}
