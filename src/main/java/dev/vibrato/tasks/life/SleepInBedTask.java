package dev.vibrato.tasks.life;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.misc.PlaceBedAndSetSpawnTask;
import dev.vibrato.tasks.misc.SleepThroughNightTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.WorldHelper;

/**
 * Goes to bed at night. Optionally sets the spawn point first by placing or using a bed; the bed itself is fetched
 * (crafted from wool and planks) by the existing bed tasks when none is available.
 */
public class SleepInBedTask extends Task {

    private final boolean _setSpawn;
    private final PlaceBedAndSetSpawnTask _spawnTask = new PlaceBedAndSetSpawnTask();
    private final SleepThroughNightTask _sleepTask = new SleepThroughNightTask();
    private boolean _spawnDone;

    public SleepInBedTask(boolean setSpawn) {
        _setSpawn = setSpawn;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _spawnDone = !_setSpawn;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (!_spawnDone) {
            if (_spawnTask.isSpawnSet()) {
                _spawnDone = true;
            } else {
                setDebugState("Setting spawn point in a bed");
                return _spawnTask;
            }
        }
        setDebugState("Sleeping until morning");
        return _sleepTask;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        // Spawn can only be set and sleep only starts at night, so daytime with nothing asleep means we are done.
        return !WorldHelper.canSleep() && !mod.getPlayer().isSleeping();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SleepInBedTask task && task._setSpawn == _setSpawn;
    }

    @Override
    protected String toDebugString() {
        return _setSpawn ? "Setting spawn point, then sleeping until morning" : "Sleeping until morning";
    }
}
