package dev.vibrato.tasks.build;

import dev.vibrato.Vibrato;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Optional;

/** A flat bridge that starts on the ground in front of the player and runs the way the player faces. */
public class BuildBridgeTask extends BuildBlueprintTask {

    public BuildBridgeTask(Palette palette, int length, int width) {
        super(Blueprints.bridge(length, width), null, palette);
    }

    @Override
    protected Optional<BlockPos> anchor(Vibrato mod) {
        Direction facing = facing(mod);
        return Optional.of(mod.getPlayer().getBlockPos().down().offset(facing));
    }

    @Override
    protected Blueprint orient(Vibrato mod, Blueprint blueprint) {
        // The blueprint runs along +x; each quarter turn rotates it towards the player's facing.
        int turns = switch (facing(mod)) {
            case SOUTH -> 1;
            case WEST -> 2;
            case NORTH -> 3;
            default -> 0;
        };
        Blueprint turned = blueprint;
        for (int i = 0; i < turns; i++) {
            turned = turned.quarterTurned();
        }
        return turned;
    }

    private static Direction facing(Vibrato mod) {
        return Direction.fromRotation(mod.getPlayer().getYaw());
    }
}
