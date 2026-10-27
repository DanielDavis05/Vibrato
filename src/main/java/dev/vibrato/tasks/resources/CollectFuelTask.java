package dev.vibrato.tasks.resources;

import dev.vibrato.Vibrato;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.movement.DefaultGoToDimensionTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.Dimension;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.item.Items;

// TODO: Make this collect more than just coal. It should smartly pick alternative sources if coal is too far away or if we simply cannot get a wooden pick.
public class CollectFuelTask extends Task {

    private final double _targetFuel;

    public CollectFuelTask(double targetFuel) {
        _targetFuel = targetFuel;
    }

    @Override
    protected void onStart(Vibrato mod) {
        // Nothing
    }

    @Override
    protected Task onTick(Vibrato mod) {

        switch (WorldHelper.getCurrentDimension()) {
            case OVERWORLD -> {
                // Just collect coal for now.
                setDebugState("Collecting coal.");
                return TaskCatalogue.getItemTask(Items.COAL, (int) Math.ceil(_targetFuel / 8));
            }
            case END -> {
                setDebugState("Going to overworld, since, well, no more fuel can be found here.");
                return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
            }
            case NETHER -> {
                setDebugState("Going to overworld, since we COULD use wood but wood confuses the bot. A bug at the moment.");
                return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
            }
            //return TaskCatalogue.getItemTask("planks", (int) Math.ceil(_targetFuel));
        }
        setDebugState("INVALID DIMENSION: " + WorldHelper.getCurrentDimension());
        return null;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        // Nothing
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof CollectFuelTask task) {
            return Math.abs(task._targetFuel - _targetFuel) < 0.01;
        }
        return false;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return mod.getItemStorage().getItemCountInventoryOnly(Items.COAL) >= _targetFuel;
    }

    @Override
    protected String toDebugString() {
        return "Collect Fuel: x" + _targetFuel;
    }
}
