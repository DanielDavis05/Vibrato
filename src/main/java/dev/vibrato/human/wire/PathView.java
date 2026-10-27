package dev.vibrato.human.wire;

import java.util.List;

/**
 * What the key layer knows about the path this tick, with no Minecraft or Baritone types.
 *
 * @param kind     movement Baritone is executing, {@link MovementKind#OTHER} when unknown
 * @param nodes    remaining path nodes, starting with the source node of the current movement, block centres
 * @param x        player position
 * @param vx       player velocity in blocks per tick
 * @param onGround whether the player is on the ground
 */
public record PathView(
        MovementKind kind,
        List<Node> nodes,
        double x, double y, double z,
        double vx, double vy, double vz,
        boolean onGround
) {

    /** A path node as world coordinates. */
    public record Node(double x, double y, double z) {
    }

    public PathView {
        nodes = List.copyOf(nodes);
    }
}
