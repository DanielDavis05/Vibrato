package dev.vibrato.tasks.combat;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.resources.CollectFoodTask;
import dev.vibrato.tasksystem.Task;

/** Stocks food by running the food collector, which hunts animals (and gathers crops) until enough is held. */
public class HuntForFoodTask extends Task {

    private final int _units;
    private final CollectFoodTask _food;

    public HuntForFoodTask(int units) {
        _units = units;
        _food = new CollectFoodTask(units);
    }

    @Override
    protected void onStart(Vibrato mod) {

    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_food.isFinished(mod)) {
            return null;
        }
        setDebugState("Hunting for " + _units + " food units");
        return _food;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _food.isFinished(mod);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof HuntForFoodTask task && task._units == _units;
    }

    @Override
    protected String toDebugString() {
        return "Hunting for " + _units + " food units";
    }
}
