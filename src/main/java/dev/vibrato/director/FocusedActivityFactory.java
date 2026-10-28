package dev.vibrato.director;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.travel.BoatTravelTask;
import dev.vibrato.tasksystem.Task;
import net.minecraft.util.math.BlockPos;
import java.util.function.Supplier;

/** A focused vehicle goal must exercise that vehicle; general Director activities keep their alternatives. */
final class FocusedActivityFactory {
    private FocusedActivityFactory() {}

    static Task create(Activity activity, ParamValues params, boolean goal, Vibrato mod, Supplier<BlockPos> boatTarget) {
        if (goal && activity.id().equals("travel_boat_cross")) return new BoatTravelTask(boatTarget.get());
        return activity.create(mod, params);
    }
}
