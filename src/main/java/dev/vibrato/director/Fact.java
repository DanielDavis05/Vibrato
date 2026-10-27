package dev.vibrato.director;

import java.util.Objects;

/**
 * One piece of symbolic world knowledge the planner reasons about, e.g. {@code has:iron_pickaxe}, {@code dim:nether},
 * {@code spawn:set}, {@code level:30}. Keys are lowercase, colon-namespaced and stable: they are persisted in
 * coverage files and compared by value.
 */
public record Fact(String key) {

    public Fact {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Fact key must not be blank");
        }
    }

    public static Fact of(String key) {
        return new Fact(key);
    }

    /** {@code has:<item>} */
    public static Fact has(String item) {
        return new Fact("has:" + item);
    }

    /** {@code dim:<dimension>} */
    public static Fact dimension(String dimension) {
        return new Fact("dim:" + dimension);
    }

    @Override
    public String toString() {
        return key;
    }
}
