package dev.vibrato.tasks.resources;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.MiningRequirement;
import dev.vibrato.util.helpers.StorageHelper;
import net.minecraft.item.Item;

public class GetBuildingMaterialsTask extends Task {
    private final int _count;
    public GetBuildingMaterialsTask(int count) {
        _count = count;
    }

    @Override
    protected void onStart(Vibrato mod) {

    }

    @Override
    protected Task onTick(Vibrato mod) {
        Item[] throwaways = mod.getModSettings().getThrowawayItems(mod, true);
        return new MineAndCollectTask(new ItemTarget[] {new ItemTarget(throwaways, _count)}, MiningRequirement.WOOD);
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof GetBuildingMaterialsTask task) {
            return task._count == _count;
        }
        return false;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return StorageHelper.getBuildingMaterialCount(mod) >= _count;
    }

    @Override
    protected String toDebugString() {
        return "Collecting " + _count + " building materials.";
    }
}
