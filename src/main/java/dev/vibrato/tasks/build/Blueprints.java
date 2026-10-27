package dev.vibrato.tasks.build;

import dev.vibrato.tasks.build.Blueprint.Placement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.vibrato.tasks.build.BlockKind.*;

/** Deterministic generators for the structures the build tasks can raise. All sizes are validated. */
public final class Blueprints {

    private Blueprints() {}

    /** Single-storey room with a door on the z=0 face, windows and simple furniture. */
    public static Blueprint hut(int width, int depth, int height) {
        requireRange("hut width", width, 3, 6);
        requireRange("hut depth", depth, 3, 6);
        requireRange("hut height", height, 3, 5);
        return building("hut", width, depth, 1, height, false);
    }

    /** Stacked rooms joined by a ladder through a hole in each upper floor. */
    public static Blueprint house(int width, int depth, int floors) {
        requireRange("house width", width, 4, 6);
        requireRange("house depth", depth, 4, 6);
        requireRange("house floors", floors, 1, 2);
        return building("house", width, depth, floors, 4, floors > 1);
    }

    /** Round tower with a ladder up the inside wall and a platform every four blocks. */
    public static Blueprint tower(int radius, int height) {
        requireRange("tower radius", radius, 2, 3);
        requireRange("tower height", height, 6, 24);
        int inner = radius * radius - radius;
        int outer = radius * radius + radius;
        Builder b = new Builder();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int d2 = x * x + z * z;
                if (d2 > outer) {
                    continue;
                }
                if (d2 > inner) {
                    for (int dy = 1; dy <= height; dy++) {
                        b.put(x, dy, z, WALL);
                    }
                } else {
                    b.put(x, 1, z, FLOOR);
                }
            }
        }
        for (int floor = 5; floor <= height; floor += 4) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int d2 = x * x + z * z;
                    if (d2 <= inner) {
                        b.put(x, floor, z, FLOOR);
                    }
                }
            }
        }
        for (int dy = 2; dy <= height; dy++) {
            b.put(0, dy, radius - 1, LADDER);
        }
        b.put(0, 1, -radius, DOOR);
        b.remove(0, 2, -radius);
        b.put(0, 3, radius, WINDOW);
        b.put(radius, 3, 0, WINDOW);
        b.put(-radius, 3, 0, WINDOW);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= outer) {
                    b.put(x, height + 1, z, ROOF);
                }
            }
        }
        return b.build("tower");
    }

    /** Flat deck along +x with railings along its edges. */
    public static Blueprint bridge(int length, int width) {
        requireRange("bridge length", length, 1, 64);
        requireRange("bridge width", width, 1, 3);
        Builder b = new Builder();
        for (int x = 0; x < length; x++) {
            for (int z = 0; z < width; z++) {
                b.put(x, 1, z, FLOOR);
            }
            b.put(x, 2, 0, FENCE);
            if (width > 1) {
                b.put(x, 2, width - 1, FENCE);
            }
        }
        return b.build("bridge");
    }

    /** Free-standing wall along +x with a crenellated top. */
    public static Blueprint wall(int length, int height) {
        requireRange("wall length", length, 1, 64);
        requireRange("wall height", height, 1, 8);
        Builder b = new Builder();
        for (int x = 0; x < length; x++) {
            for (int dy = 1; dy <= height; dy++) {
                b.put(x, dy, 0, WALL);
            }
            if (x % 2 == 0) {
                b.put(x, height + 1, 0, WALL);
            }
        }
        return b.build("wall");
    }

    /** Nine-wide plot: farmland either side of a water channel running along z, on the ground layer. */
    public static Blueprint farm(int rows) {
        requireRange("farm rows", rows, 1, 9);
        Builder b = new Builder();
        for (int z = 0; z < rows; z++) {
            for (int x = 0; x < 9; x++) {
                b.put(x, 0, z, x == 4 ? WATER : FARMLAND);
            }
        }
        return b.build("farm");
    }

    /** Stepped pyramid, each layer one block smaller on every side. */
    public static Blueprint pyramid(int base) {
        requireRange("pyramid base", base, 3, 13);
        Builder b = new Builder();
        for (int layer = 0; base - 2 * layer >= 1; layer++) {
            int side = base - 2 * layer;
            for (int x = layer; x < layer + side; x++) {
                for (int z = layer; z < layer + side; z++) {
                    b.put(x, 1 + layer, z, WALL);
                }
            }
        }
        return b.build("pyramid");
    }

    /** Three-by-three plinth with a single-block pillar rising from its centre. */
    public static Blueprint obelisk(int height) {
        requireRange("obelisk height", height, 5, 20);
        Builder b = new Builder();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                b.put(x, 1, z, WALL);
            }
        }
        for (int dy = 2; dy <= height + 1; dy++) {
            b.put(1, dy, 1, PILLAR);
        }
        return b.build("obelisk");
    }

    /** Four wide by five tall obsidian ring in the XY plane, interior left open for lighting. */
    public static Blueprint portalFrame() {
        Builder b = new Builder();
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 5; y++) {
                if (x == 0 || x == 3 || y == 0 || y == 4) {
                    b.put(x, 1 + y, 0, OBSIDIAN);
                }
            }
        }
        return b.build("portal_frame");
    }

    /** A straight row of path blocks along +x on the ground layer. */
    public static Blueprint pathLine(int length) {
        requireRange("path length", length, 1, 64);
        Builder b = new Builder();
        for (int x = 0; x < length; x++) {
            b.put(x, 0, 0, PATH);
        }
        return b.build("path");
    }

    /** Square fence enclosure with a gate in the middle of the z=0 side. */
    public static Blueprint pen(int width, int depth) {
        requireRange("pen width", width, 4, 10);
        requireRange("pen depth", depth, 4, 10);
        Builder b = new Builder();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (x == 0 || x == width - 1 || z == 0 || z == depth - 1) {
                    b.put(x, 1, z, FENCE);
                }
            }
        }
        b.put(width / 2, 1, 0, GATE);
        return b.build("pen");
    }

    private static Blueprint building(String name, int width, int depth, int levels, int wallHeight, boolean ladder) {
        int door = width / 2;
        Builder b = new Builder();
        for (int level = 0; level < levels; level++) {
            int floor = 1 + level * wallHeight;
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    boolean ring = x == 0 || x == width - 1 || z == 0 || z == depth - 1;
                    if (ring) {
                        for (int dy = floor; dy < floor + wallHeight; dy++) {
                            b.put(x, dy, z, WALL);
                        }
                    } else {
                        b.put(x, floor, z, FLOOR);
                    }
                }
            }
            b.put(0, floor + 2, depth / 2, WINDOW);
            b.put(width - 1, floor + 2, depth / 2, WINDOW);
            b.put(door, floor + 2, depth - 1, WINDOW);
        }
        if (ladder) {
            for (int dy = 2; dy <= levels * wallHeight; dy++) {
                b.put(1, dy, 1, LADDER);
            }
        }
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                b.put(x, 1 + levels * wallHeight, z, ROOF);
            }
        }
        b.put(door, 1, 0, DOOR);
        b.remove(door, 2, 0);
        furnish(b, width, depth, door, TORCH, List.of(new int[]{1, 1}, new int[]{width - 2, depth - 2}, new int[]{1, depth - 2}));
        furnish(b, width, depth, door, CHEST, List.of(new int[]{width - 2, 1}, new int[]{width - 2, depth - 2}));
        if (levels == 1) {
            furnish(b, width, depth, door, BED, List.of(new int[]{1, depth - 2}, new int[]{width - 2, depth - 2}));
        }
        return b.build(name);
    }

    /** Puts one piece of furniture on the first candidate cell that is free and keeps the entrance clear. */
    private static void furnish(Builder b, int width, int depth, int door, BlockKind kind, List<int[]> candidates) {
        for (int[] c : candidates) {
            int x = c[0];
            int z = c[1];
            boolean interior = x >= 1 && x <= width - 2 && z >= 1 && z <= depth - 2;
            boolean entrance = x == door && z == 1;
            if (interior && !entrance && !b.has(x, 2, z)) {
                b.put(x, 2, z, kind);
                return;
            }
        }
    }

    private static void requireRange(String what, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(what + " must be in [" + min + ", " + max + "] but was " + value);
        }
    }

    private static final class Builder {
        private final Map<List<Integer>, Placement> cells = new LinkedHashMap<>();

        void put(int x, int y, int z, BlockKind kind) {
            cells.put(List.of(x, y, z), new Placement(x, y, z, kind));
        }

        void remove(int x, int y, int z) {
            cells.remove(List.of(x, y, z));
        }

        boolean has(int x, int y, int z) {
            return cells.containsKey(List.of(x, y, z));
        }

        Blueprint build(String name) {
            return new Blueprint(name, List.copyOf(cells.values()));
        }
    }
}
