package dev.vibrato.tasks.misc;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;

public class SleepThroughNightTask extends Task {

    @Override
    protected void onStart(Vibrato mod) {

    }

    @Override
    protected Task onTick(Vibrato mod) {
        return new PlaceBedAndSetSpawnTask().stayInBed();
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SleepThroughNightTask;
    }

    @Override
    protected String toDebugString() {
        return "Sleeping through the night";
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        // We're in daytime
        int time = (int)(mod.getWorld().getTimeOfDay() % 24000);
        return 0 <= time && time < 13000;
    }
}
