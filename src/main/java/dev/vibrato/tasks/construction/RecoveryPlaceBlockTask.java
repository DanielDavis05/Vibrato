package dev.vibrato.tasks.construction;

import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.movement.PickupDroppedItemTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.LookHelper;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Bounded unstuck placement: never spend an item protected by the interrupted task. */
public class RecoveryPlaceBlockTask extends Task {
    private static final Block[] CHEAP = {Blocks.DIRT, Blocks.COBBLESTONE, Blocks.COBBLED_DEEPSLATE,
            Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.NETHERRACK};
    private final BlockPos target;
    // The interrupted resource task may pop its behaviour frame before this task
    // ticks. Preserve its protection snapshot for the entire recovery attempt.
    private final Set<Item> interruptedProtected;
    private BlockPos dirtToMine;
    private boolean attemptedMine;
    private boolean done;
    private PlaceBlockNearbyTask placement;

    public RecoveryPlaceBlockTask(Vibrato mod, BlockPos target) {
        this.target = target;
        this.interruptedProtected = Arrays.stream(CHEAP).map(Block::asItem)
                .filter(mod.getBehaviour()::isProtected).collect(Collectors.toUnmodifiableSet());
    }

    private boolean isProtected(Vibrato mod, Item item) {
        return RecoveryMaterialSelector.isProtected(interruptedProtected.contains(item),
                mod.getBehaviour().isProtected(item));
    }

    @Override protected void onStart(Vibrato mod) { }

    @Override protected Task onTick(Vibrato mod) {
        if (!StorageHelper.getItemStackInCursorSlot().isEmpty()) {
            Debug.logInternal("Recovery step skipped: cursor contains an item; preserving it before closing a screen");
            done = true;
            return null;
        }
        Block selected = RecoveryMaterialSelector.firstUsable(CHEAP,
                block -> mod.getPlayer().getInventory().main.stream().anyMatch(stack -> stack.isOf(block.asItem())),
                block -> isProtected(mod, block.asItem()));
        if (selected != null) {
            if (placement == null || !placementBlockMatches(selected)) {
                int inventorySlot = -1;
                for (int i = 0; i < mod.getPlayer().getInventory().main.size(); i++) {
                    if (mod.getPlayer().getInventory().main.get(i).isOf(selected.asItem())) {
                        inventorySlot = i;
                        break;
                    }
                }
                Debug.logInternal("Recovery step material=" + selected + " target=" + target.toShortString()
                        + " inventorySlot=" + inventorySlot + " hotbar=" + (inventorySlot < 9)
                        + " protected=false");
                placement = new PlaceBlockNearbyTask(pos -> pos.equals(target), selected);
                selectedBlock = selected;
            }
            return placement;
        }
        if (dirtToMine != null && isProtected(mod, Items.DIRT)) {
            Debug.logInternal("Recovery dirt became protected; abandoning collection");
            done = true;
            return null;
        }
        if (!attemptedMine && !isProtected(mod, Items.DIRT)
                && mod.getItemStorage().getSlotThatCanFitInPlayerInventory(new ItemStack(Items.DIRT), true).isPresent()) {
            BlockPos feet = mod.getPlayer().getBlockPos();
            for (Direction direction : Direction.Type.HORIZONTAL) {
                BlockPos candidate = feet.offset(direction);
                if (candidate.equals(target) || !mod.getWorld().getBlockState(candidate).isOf(Blocks.DIRT)
                        || !WorldHelper.canBreak(mod, candidate) || LookHelper.getReach(candidate).isEmpty()) continue;
                boolean adjacentFluid = false;
                for (Direction side : Direction.values()) {
                    if (!mod.getWorld().getFluidState(candidate.offset(side)).isEmpty()) {
                        adjacentFluid = true;
                        break;
                    }
                }
                if (adjacentFluid) continue;
                dirtToMine = candidate;
                attemptedMine = true;
                Debug.logInternal("Recovery step: collecting adjacent unprotected dirt at " + candidate.toShortString());
                return new DestroyBlockTask(candidate);
            }
        }
        if (dirtToMine != null) {
            if (mod.getWorld().getBlockState(dirtToMine).isOf(Blocks.DIRT)) return new DestroyBlockTask(dirtToMine);
            return new PickupDroppedItemTask(Items.DIRT, 1, false);
        }
        Debug.logInternal("Recovery step unavailable: no unprotected cheap block in 36 inventory slots; protected="
                + Arrays.toString(Arrays.stream(CHEAP).filter(b -> isProtected(mod, b.asItem())).toArray())
                + ", target=" + target.toShortString());
        done = true;
        return null;
    }
    private Block selectedBlock;
    private boolean placementBlockMatches(Block block) { return selectedBlock == block; }
    @Override protected void onStop(Vibrato mod, Task interruptTask) { }
    @Override protected boolean isEqual(Task other) { return other instanceof RecoveryPlaceBlockTask task && target.equals(task.target); }
    @Override public boolean isFinished(Vibrato mod) { return done || (placement != null && placement.isFinished(mod)); }
    @Override protected String toDebugString() { return "Place recovery step at " + target.toShortString(); }
}
