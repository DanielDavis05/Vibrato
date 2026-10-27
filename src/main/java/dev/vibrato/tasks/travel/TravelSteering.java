package dev.vibrato.tasks.travel;

import dev.vibrato.Vibrato;
import dev.vibrato.util.helpers.LookHelper;
import baritone.api.utils.input.Input;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Shared controls for the travel tasks: camera aim (human-limited) and key handling for vehicles. */
final class TravelSteering {

    private TravelSteering() {}

    static void lookAt(Vibrato mod, Vec3d at) {
        LookHelper.lookAt(mod, LookHelper.getLookRotation(mod, at));
    }

    /** Minecraft yaw that points from the origin towards {@code (dx, dz)}. */
    static float bearingDegrees(double dx, double dz) {
        return MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)));
    }

    static void releaseAll(Vibrato mod, Input... inputs) {
        for (Input input : inputs) {
            mod.getInputControls().release(input);
        }
    }
}
