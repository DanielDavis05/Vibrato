package dev.vibrato.nav;

/**
 * A remembered position. {@code kind} is one of {@link WaypointKind} or a custom string; {@code dimension} is the
 * dimension identifier, such as {@code minecraft:overworld}.
 */
public record Waypoint(String kind, String dimension, int x, int y, int z) {
}
