package dev.vibrato.nav;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Named waypoints, persisted as JSON. Names are unique keys, so several chests can share a kind and be told apart by
 * name.
 */
public final class WaypointMemory {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Waypoint>> TYPE = new TypeReference<>() {
    };

    private final Map<String, Waypoint> _waypoints = new HashMap<>();

    public void set(String name, Waypoint waypoint) {
        _waypoints.put(name, waypoint);
    }

    public Optional<Waypoint> get(String name) {
        return Optional.ofNullable(_waypoints.get(name));
    }

    /**
     * The closest waypoint of {@code kind} in {@code dimension}, measured in straight-line distance from the given
     * position.
     */
    public Optional<Waypoint> nearest(String kind, String dimension, int x, int y, int z) {
        Waypoint best = null;
        double bestDistanceSquared = Double.MAX_VALUE;
        for (Waypoint waypoint : _waypoints.values()) {
            if (!kind.equals(waypoint.kind()) || !dimension.equals(waypoint.dimension())) {
                continue;
            }
            double dx = waypoint.x() - x;
            double dy = waypoint.y() - y;
            double dz = waypoint.z() - z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                best = waypoint;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Reads a memory from {@code file}. A missing, empty or unreadable file gives an empty memory.
     */
    public static WaypointMemory load(Path file) {
        WaypointMemory memory = new WaypointMemory();
        if (!Files.isRegularFile(file)) {
            return memory;
        }
        try {
            Map<String, Waypoint> stored = MAPPER.readValue(file.toFile(), TYPE);
            if (stored != null) {
                stored.forEach((name, waypoint) -> {
                    if (name != null && waypoint != null) {
                        memory._waypoints.put(name, waypoint);
                    }
                });
            }
        } catch (IOException e) {
            return new WaypointMemory();
        }
        return memory;
    }

    public void save(Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), _waypoints);
    }
}
