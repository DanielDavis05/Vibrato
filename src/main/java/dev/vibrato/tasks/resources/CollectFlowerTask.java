package dev.vibrato.tasks.resources;

import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.MiningRequirement;
import dev.vibrato.util.helpers.ItemHelper;

public class CollectFlowerTask extends MineAndCollectTask {
    public CollectFlowerTask(int count) {
        super(new ItemTarget(ItemHelper.FLOWER, count), ItemHelper.itemsToBlocks(ItemHelper.FLOWER), MiningRequirement.HAND);
    }
}
