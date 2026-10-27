package dev.vibrato.advancement;

import dev.vibrato.director.FeatureArea;

import java.util.List;
import java.util.Optional;

/**
 * Curated overworld advancements whose trigger is obtaining an item, so the existing {@code get} command finishes
 * them. Advancements that need an action (sleeping, trading, breeding, killing, planting, portals) are deliberately
 * absent until a command or task can really perform that action.
 */
public final class AdvancementGoals {

    private AdvancementGoals() {}

    public static final List<AdvancementGoal> ALL = List.of(
            new AdvancementGoal("minecraft:story/mine_stone", FeatureArea.GATHER, "get cobblestone 1"),
            new AdvancementGoal("minecraft:story/upgrade_tools", FeatureArea.CRAFT, "get stone_pickaxe 1"),
            new AdvancementGoal("minecraft:story/smelt_iron", FeatureArea.SMELT, "get iron_ingot 1"),
            new AdvancementGoal("minecraft:story/obtain_armor", FeatureArea.GEAR, "get iron_chestplate 1"),
            new AdvancementGoal("minecraft:story/lava_bucket", FeatureArea.GATHER, "get lava_bucket 1"),
            new AdvancementGoal("minecraft:story/iron_tools", FeatureArea.CRAFT, "get iron_pickaxe 1"),
            new AdvancementGoal("minecraft:story/deflect_arrow", FeatureArea.COMBAT, "get shield 1"),
            new AdvancementGoal("minecraft:story/form_obsidian", FeatureArea.GATHER, "get obsidian 1"),
            new AdvancementGoal("minecraft:story/mine_diamond", FeatureArea.GATHER, "get diamond 1"),
            new AdvancementGoal("minecraft:story/shiny_gear", FeatureArea.GEAR, "get diamond_chestplate 1"),
            new AdvancementGoal("minecraft:husbandry/make_a_sign", FeatureArea.CRAFT, "get sign 1"));

    public static Optional<AdvancementGoal> find(String id) {
        return ALL.stream().filter(goal -> goal.id().equals(id)).findFirst();
    }
}
