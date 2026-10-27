package dev.vibrato.tasks.resources;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.ResourceTask;
import dev.vibrato.tasks.entity.DoToClosestEntityTask;
import dev.vibrato.tasks.movement.DefaultGoToDimensionTask;
import dev.vibrato.tasks.movement.GetToEntityTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.Dimension;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.item.Items;

public class CollectEggsTask extends ResourceTask {

    private final int _count;

    private final DoToClosestEntityTask _waitNearChickens;

    private Vibrato _mod;

    public CollectEggsTask(int targetCount) {
        super(Items.EGG, targetCount);
        _count = targetCount;
        _waitNearChickens = new DoToClosestEntityTask(chicken -> new GetToEntityTask(chicken, 5), ChickenEntity.class);
    }

    @Override
    protected boolean shouldAvoidPickingUp(Vibrato mod) {
        return false;
    }

    @Override
    protected void onResourceStart(Vibrato mod) {
        _mod = mod;
    }

    @Override
    protected Task onResourceTick(Vibrato mod) {
        // Wrong dimension check.
        if (_waitNearChickens.wasWandering() && WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            setDebugState("Going to right dimension.");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }
        // Just wait around chickens.
        setDebugState("Waiting around chickens. Yes.");
        return _waitNearChickens;
    }

    @Override
    protected void onResourceStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectEggsTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Collecting " + _count + " eggs.";
    }
}
