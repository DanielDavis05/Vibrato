package dev.vibrato.tasks.resources.wood;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.resources.CraftWithMatchingPlanksTask;
import dev.vibrato.util.CraftingRecipe;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.ItemHelper;
import net.minecraft.item.Item;

public class CollectWoodenButtonTask extends CraftWithMatchingPlanksTask {

    public CollectWoodenButtonTask(Item[] targets, ItemTarget planks, int count) {
        super(targets,  woodItems -> woodItems.button, createRecipe(planks), new boolean[]{true, true, false, false}, count);
    }

    public CollectWoodenButtonTask(Item target, String plankCatalogueName, int count) {
        this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
    }

    public CollectWoodenButtonTask(int count) {
        this(ItemHelper.WOOD_BUTTON, TaskCatalogue.getItemTarget("planks", 1), count);
    }


    private static CraftingRecipe createRecipe(ItemTarget planks) {
        ItemTarget p = planks;
        return CraftingRecipe.newShapedRecipe(new ItemTarget[]{p, null, null, null}, 1);
    }
}
