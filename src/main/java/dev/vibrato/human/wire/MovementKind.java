package dev.vibrato.human.wire;

/**
 * Kind of the path movement Baritone is executing. Only what {@link ContextBuilder} needs to pick jump tolerances.
 */
public enum MovementKind {
    TRAVERSE,
    ASCEND,
    DESCEND,
    DIAGONAL,
    FALL,
    PARKOUR,
    CLIMB,
    OTHER;

    /**
     * Maps a Baritone movement class name such as {@code MovementAscend}, with or without the prefix. Anything not
     * recognised is {@link #OTHER}, which gets no timing tolerance.
     */
    public static MovementKind fromClassName(String simpleName) {
        if (simpleName == null) {
            return OTHER;
        }
        String name = simpleName.startsWith("Movement") ? simpleName.substring("Movement".length()) : simpleName;
        for (MovementKind kind : values()) {
            if (kind.name().equalsIgnoreCase(name)) {
                return kind;
            }
        }
        return OTHER;
    }
}
