package dev.vibrato.tasks.resources.wood;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.resources.CraftWithMatchingPlanksTask;
import dev.vibrato.util.CraftingRecipe;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.ItemHelper;
import net.minecraft.item.Item;

public class CollectWoodenDoorTask extends CraftWithMatchingPlanksTask {

    public CollectWoodenDoorTask(Item[] targets, ItemTarget planks, int count) {
        super(targets,  woodItems -> woodItems.door, createRecipe(planks), new boolean[]{true, true, false, true, true, false, true, true, false}, count);
    }

    public CollectWoodenDoorTask(Item target, String plankCatalogueName, int count) {
        this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
    }

    public CollectWoodenDoorTask(int count) {
        this(ItemHelper.WOOD_DOOR, TaskCatalogue.getItemTarget("planks", 1), count);
    }

    private static CraftingRecipe createRecipe(ItemTarget planks) {
        ItemTarget p = planks;
        ItemTarget o = null;
        return CraftingRecipe.newShapedRecipe(new ItemTarget[]{p, p, o, p, p, o, p, p, o}, 3);
    }
}
