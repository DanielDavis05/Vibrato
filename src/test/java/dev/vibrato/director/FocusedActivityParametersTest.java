package dev.vibrato.director;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FocusedActivityParametersTest {
    private static SyntheticActivity activity(String id, ParamSpec... specs) {
        return new SyntheticActivity(id, FeatureArea.CHEST, Set.of(), Set.of(), List.of(specs));
    }
    @Test void cheapDepositProfileIsDeterministicAndNeverRequestsRareBulkItems() {
        var goal = activity("chest_store_items", ParamSpec.choice("item", "dirt", "cobblestone", "sand", "gravel", "diamond"), ParamSpec.ints("count", 1, 32));
        var original = new ParamValues(Map.of("item", "diamond", "count", 32));
        var fixed = FocusedActivityParameters.constrain(goal, original, 424242);
        assertEquals(fixed, FocusedActivityParameters.constrain(goal, original, 424242));
        for (long seed = 0; seed < 100; seed++) {
            var values = FocusedActivityParameters.constrain(goal, original, seed);
            assertEquals("dirt", values.getString("item"));
            assertTrue(values.getInt("count") >= 4 && values.getInt("count") <= 8);
        }
        assertEquals("diamond", original.getString("item"));
        assertEquals(32, original.getInt("count"));
    }
    @Test void goalProfileKeepsTheExactProviderPlanAndItsOriginalParameters() {
        var provider = new PlannedStep(activity("provider", ParamSpec.ints("count", 1, 64)), new ParamValues(Map.of("count", 64)));
        var goal = new PlannedStep(activity("smelt_glass", ParamSpec.ints("count", 1, 64)), new ParamValues(Map.of("count", 64)));
        var constrained = FocusedActivityParameters.constrain(new Plan(List.of(provider, goal)), "smelt_glass", 424242);
        assertSame(provider, constrained.steps().getFirst());
        assertSame(goal.activity(), constrained.steps().getLast().activity());
        assertTrue(constrained.steps().getLast().params().getInt("count") <= 4);
        assertEquals(64, goal.params().getInt("count"));
    }
    @Test void fishingTradeFarmingAndBuildingHaveSmallValidGoals() {
        var fish = activity("fish", ParamSpec.ints("count", 2, 16));
        var trade = activity("trade_for_item", ParamSpec.choice("item", "bread", "saddle"), ParamSpec.ints("count", 1, 16));
        var farm = activity("farm_crop", ParamSpec.choice("crop", "wheat", "carrot"), ParamSpec.ints("rows", 2, 8));
        var hut = activity("build_hut", ParamSpec.ints("width", 3, 6), ParamSpec.ints("depth", 3, 6), ParamSpec.choice("palette", "oak", "cobblestone", "stone_bricks"));
        var wall = activity("build_wall", ParamSpec.ints("length", 4, 32), ParamSpec.ints("height", 2, 6), ParamSpec.choice("palette", "oak", "cobblestone", "stone_bricks"));
        for (long seed = 0; seed < 50; seed++) {
            var f = FocusedActivityParameters.constrain(fish, new ParamValues(Map.of("count", 16)), seed);
            assertTrue(f.getInt("count") >= 2 && f.getInt("count") <= 3);
            var t = FocusedActivityParameters.constrain(trade, new ParamValues(Map.of("item", "saddle", "count", 16)), seed);
            assertEquals("bread", t.getString("item")); assertEquals(1, t.getInt("count"));
            var farmValues = FocusedActivityParameters.constrain(farm, new ParamValues(Map.of("crop", "carrot", "rows", 8)), seed);
            assertEquals("wheat", farmValues.getString("crop")); assertEquals(2, farmValues.getInt("rows"));
            var h = FocusedActivityParameters.constrain(hut, new ParamValues(Map.of("width", 6, "depth", 6, "palette", "stone_bricks")), seed);
            assertEquals(3, h.getInt("width")); assertEquals(3, h.getInt("depth"));
            var w = FocusedActivityParameters.constrain(wall, new ParamValues(Map.of("length", 32, "height", 6, "palette", "stone_bricks")), seed);
            assertTrue(w.getInt("length") >= 4 && w.getInt("length") <= 8);
            assertTrue(w.getInt("height") >= 2 && w.getInt("height") <= 3);
            assertTrue(Set.of("oak", "cobblestone").contains(w.getString("palette")));
        }
    }
    @Test void changedParameterDomainsRejectIncompatibleProfilesInsteadOfInventingInputs() {
        var incompatible = activity("fish", ParamSpec.ints("count", 10, 20));
        assertThrows(IllegalStateException.class, () -> FocusedActivityParameters.constrain(incompatible,
                new ParamValues(Map.of("count", 10)), 424242));
    }
}
