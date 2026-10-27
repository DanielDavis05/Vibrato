package dev.vibrato.tasks.resources;

import dev.vibrato.Vibrato;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.MiningRequirement;
import dev.vibrato.util.helpers.ItemHelper;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class ShearAndCollectBlockTask extends MineAndCollectTask {

    public ShearAndCollectBlockTask(ItemTarget[] itemTargets, Block... blocksToMine) {
        super(itemTargets, blocksToMine, MiningRequirement.HAND);
    }

    public ShearAndCollectBlockTask(Item[] items, int count, Block... blocksToMine) {
        this(new ItemTarget[]{new ItemTarget(items, count)}, blocksToMine);
    }

    public ShearAndCollectBlockTask(Item item, int count, Block... blocksToMine) {
        this(new Item[]{item}, count, blocksToMine);
    }

    @Override
    protected void onStart(Vibrato mod) {
        mod.getBehaviour().push();
        mod.getBehaviour().forceUseTool((blockState, itemStack) ->
                itemStack.getItem() == Items.SHEARS && ItemHelper.areShearsEffective(blockState.getBlock())
        );
        super.onStart(mod);
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        mod.getBehaviour().pop();
        super.onStop(mod, interruptTask);
    }

    @Override
    protected Task onResourceTick(Vibrato mod) {
        if (!mod.getItemStorage().hasItem(Items.SHEARS)) {
            return TaskCatalogue.getItemTask(Items.SHEARS, 1);
        }
        return super.onResourceTick(mod);
    }
}
