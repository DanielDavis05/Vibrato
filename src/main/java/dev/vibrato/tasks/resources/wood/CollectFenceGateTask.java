package dev.vibrato.tasks.resources.wood;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.resources.CraftWithMatchingPlanksTask;
import dev.vibrato.util.CraftingRecipe;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.ItemHelper;
import net.minecraft.item.Item;

public class CollectFenceGateTask extends CraftWithMatchingPlanksTask {

    public CollectFenceGateTask(Item[] targets, ItemTarget planks, int count) {
        super(targets, woodItems -> woodItems.fenceGate, createRecipe(planks), new boolean[]{false, true, false, false, true, false, false, false, false}, count);
    }

    public CollectFenceGateTask(Item target, String plankCatalogueName, int count) {
        this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
    }

    public CollectFenceGateTask(int count) {
        this(ItemHelper.WOOD_FENCE_GATE, TaskCatalogue.getItemTarget("planks", 1), count);
    }

    private static CraftingRecipe createRecipe(ItemTarget planks) {
        ItemTarget p = planks;
        ItemTarget s = TaskCatalogue.getItemTarget("stick", 1);
        return CraftingRecipe.newShapedRecipe(new ItemTarget[]{s, p, s, s, p, s, null, null, null}, 1);
    }
}
