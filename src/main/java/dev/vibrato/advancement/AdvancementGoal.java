package dev.vibrato.advancement;

import dev.vibrato.director.FeatureArea;

import java.util.Objects;

/**
 * An advancement the bots are willing to pursue, and the Vibrato command line that works towards it.
 *
 * @param id       full advancement id, e.g. {@code minecraft:story/mine_stone}
 * @param area     the feature area the goal exercises
 * @param taskSpec a command line for the existing command system, without the prefix, e.g. {@code get iron_pickaxe 1}
 */
public record AdvancementGoal(String id, FeatureArea area, String taskSpec) {

    public AdvancementGoal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(taskSpec, "taskSpec");
        if (taskSpec.isBlank()) {
            throw new IllegalArgumentException("taskSpec must not be blank for " + id);
        }
    }

    /** The id path without namespace, e.g. {@code story/mine_stone}. */
    public String path() {
        return id.substring(id.indexOf(':') + 1);
    }
}
