package dev.vibrato.director;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * The sampled value of every {@link ParamSpec} of one planned step. Immutable; iteration order is by name so the
 * string form is deterministic (it is used in logs and coverage files).
 */
public final class ParamValues {

    public static final ParamValues EMPTY = new ParamValues(Map.of());

    private final Map<String, Object> values;

    public ParamValues(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new TreeMap<>(values));
    }

    public String getString(String name) {
        return String.valueOf(require(name));
    }

    public int getInt(String name) {
        Object value = require(name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    public boolean has(String name) {
        return values.containsKey(name);
    }

    public Map<String, Object> asMap() {
        return values;
    }

    private Object require(String name) {
        Object value = values.get(name);
        if (value == null) {
            throw new IllegalArgumentException("No parameter '" + name + "' in " + values);
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ParamValues other && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
