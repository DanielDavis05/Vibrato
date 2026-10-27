package dev.vibrato.tasks.resources.wood;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.resources.CraftWithMatchingPlanksTask;
import dev.vibrato.util.CraftingRecipe;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.ItemHelper;
import net.minecraft.item.Item;

public class CollectBoatTask extends CraftWithMatchingPlanksTask {

    public CollectBoatTask(Item[] targets, ItemTarget planks, int count) {
        super(targets, woodItems -> woodItems.boat, createRecipe(planks), new boolean[]{true, false, true, true, true, true, false, false, false}, count);
    }

    public CollectBoatTask(Item target, String plankCatalogueName, int count) {
        this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
    }

    public CollectBoatTask(int count) {
        this(ItemHelper.WOOD_BOAT, TaskCatalogue.getItemTarget("planks", 1), count);
    }

    private static CraftingRecipe createRecipe(ItemTarget planks) {
        ItemTarget p = planks;
        ItemTarget o = null;
        return CraftingRecipe.newShapedRecipe(new ItemTarget[]{p, o, p, p, p, p, o, o, o}, 1);
    }
}
