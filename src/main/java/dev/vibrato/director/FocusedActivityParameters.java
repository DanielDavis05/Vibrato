package dev.vibrato.director;

import java.util.*;

/** Small mechanic-focused goals; the general Director's parameter domains stay unchanged. */
final class FocusedActivityParameters {
    private FocusedActivityParameters() {}

    static Plan constrain(Plan sampled, String goal, long seed) {
        return new Plan(sampled.steps().stream().map(step -> step.activity().id().equals(goal)
                ? new PlannedStep(step.activity(), constrain(step.activity(), step.params(), seed)) : step).toList());
    }

    static ParamValues constrain(Activity activity, ParamValues sampled, long seed) {
        Map<String, Object> values = new HashMap<>(sampled.asMap());
        SplittableRandom rng = new SplittableRandom(seed ^ activity.id().hashCode());
        switch (activity.id()) {
            case "chest_store_items", "inventory_stash_items" -> {
                values.put("item", "dirt");
                values.put("count", rng.nextInt(4, 9));
            }
            case "stash_valuables" -> values.put("min_items", 4);
            case "loot_nearby_chests", "loot_chests" -> values.put("radius", 16);
            case "smelt_iron_ingot", "smelt_gold_ingot", "smelt_glass", "smelt_charcoal" -> values.put("count", rng.nextInt(1, 5));
            case "fish" -> values.put("count", rng.nextInt(2, 4));
            case "trade_for_item" -> { values.put("item", "bread"); values.put("count", 1); }
            case "trade_for_enchanted_book" -> values.put("count", 1);
            case "farm_crop" -> { values.put("rows", 2); values.put("crop", "wheat"); }
            case "breed_animals" -> { values.put("pairs", 1); values.put("animal", "cow"); }
            case "slay_mob" -> { values.put("count", 1); values.put("mob", "zombie"); }
            case "enchant_item" -> { values.put("item", "iron_pickaxe"); values.put("minLevel", 1); }
            case "anvil_rename", "grindstone_disenchant" -> values.put("item", "iron_pickaxe");
            case "build_hut" -> {
                values.put("width", 3); values.put("depth", 3);
                values.put("palette", rng.nextBoolean() ? "oak" : "cobblestone");
            }
            case "build_wall" -> {
                values.put("length", rng.nextInt(4, 9)); values.put("height", rng.nextInt(2, 4));
                values.put("palette", rng.nextBoolean() ? "oak" : "cobblestone");
            }
            case "travel_boat_cross" -> values.put("distance", rng.nextInt(40, 65));
            default -> { }
        }
        // The same ParamSpecs still define validity; profile changes never invent unsupported inputs.
        Set<String> names = new HashSet<>();
        for (ParamSpec spec : activity.params()) {
            names.add(spec.name());
            Object value = values.get(spec.name());
            boolean valid = spec instanceof ParamSpec.Choice choice ? choice.values().contains(value)
                    : spec instanceof ParamSpec.IntRange range && value instanceof Integer number
                    && number >= range.min() && number <= range.max();
            if (!valid) throw new IllegalStateException("Invalid focused parameter " + activity.id() + "." + spec.name() + "=" + value);
        }
        if (!names.equals(values.keySet())) throw new IllegalStateException("Unexpected focused parameters for " + activity.id());
        return new ParamValues(values);
    }
}
