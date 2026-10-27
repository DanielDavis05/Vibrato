package dev.vibrato.tasks.resources;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.entity.AbstractDoToEntityTask;
import dev.vibrato.tasks.movement.IdleTask;
import dev.vibrato.tasks.movement.PickupDroppedItemTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.ItemHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import java.util.Optional;

/** Obtains shears, shears an adult sheep, and collects wool without harming sheep. */
public final class ShearWoolActivityTask extends Task {
    private final int count;
    private final LandAnimalSearchTask search = new LandAnimalSearchTask();
    private ShearOne sheepTask;
    private ItemTarget target;
    private boolean sheared;
    private int afterShearing;
    private int regrowthWait;

    public ShearWoolActivityTask(int count) {
        if (count < 1) throw new IllegalArgumentException("wool count must be positive");
        this.count = count;
    }

    @Override protected void onStart(Vibrato mod) {
        // Even with existing wool, this activity should actually obtain a new drop.
        target = new ItemTarget(ItemHelper.WOOL, ShearWoolPolicy.targetCount(count, mod.getItemStorage().getItemCount(ItemHelper.WOOL)));
        sheepTask = null;
        sheared = false;
        afterShearing = 0;
        regrowthWait = 0;
        int initialDepth = mod.getBehaviour().stackDepth();
        try {
            mod.getBehaviour().push();
            mod.getBehaviour().addProtectedItems(ItemHelper.WOOL);
            mod.getBehaviour().addProtectedItems(Items.SHEARS);
        } catch (RuntimeException | Error failure) {
            mod.getBehaviour().restoreStackDepth(initialDepth);
            throw failure;
        }
    }

    @Override protected Task onTick(Vibrato mod) {
        if (sheepTask != null && sheepTask.succeeded()) {
            sheared = true;
            sheepTask = null;
            afterShearing = 40; // allow the server's wool drops to arrive
        }
        if (!mod.getItemStorage().hasItem(Items.SHEARS)) {
            setDebugState("Getting shears");
            return TaskCatalogue.getItemTask(Items.SHEARS, 1);
        }
        if (sheared && mod.getEntityTracker().getClosestItemDrop(mod.getPlayer().getPos(), target)
                .filter(drop -> drop.isInRange(mod.getPlayer(), 16)).isPresent()) {
            setDebugState("Collecting wool");
            return new PickupDroppedItemTask(target, true);
        }
        if (afterShearing > 0) {
            afterShearing--;
            return new IdleTask();
        }
        if (sheepTask != null && sheepTask.available()) return sheepTask;
        sheepTask = null;
        Optional<Entity> ready = mod.getEntityTracker().getClosestEntity(mod.getPlayer().getPos(),
                entity -> entity instanceof SheepEntity sheep && ready(sheep), SheepEntity.class);
        if (ready.isPresent()) {
            regrowthWait = 0;
            sheepTask = new ShearOne((SheepEntity) ready.get());
            setDebugState("Shearing an adult sheep");
            return sheepTask;
        }
        // A sheared adult may regrow wool by eating grass. Give nearby animals a
        // chance, then continue searching; the activity budget bounds the search.
        boolean nearbySheep = mod.getEntityTracker().getClosestEntity(mod.getPlayer().getPos(),
                entity -> entity instanceof SheepEntity sheep && !sheep.isBaby() && sheep.isSheared()
                        && sheep.isInRange(mod.getPlayer(), 16), SheepEntity.class).isPresent();
        if (nearbySheep && regrowthWait++ < 100) {
            setDebugState("Waiting briefly for sheep to regrow wool");
            return new IdleTask();
        }
        setDebugState("Searching for shearable sheep");
        return search;
    }

    @Override public boolean isFinished(Vibrato mod) {
        return target != null && ShearWoolPolicy.finished(sheared, mod.getItemStorage().getItemCount(ItemHelper.WOOL), target.getTargetCount());
    }

    private static boolean ready(SheepEntity sheep) {
        return ShearWoolPolicy.ready(sheep.isAlive() && !sheep.isRemoved(), sheep.isBaby(), sheep.isShearable(), sheep.isSheared());
    }

    @Override protected void onStop(Vibrato mod, Task interruptTask) { mod.getBehaviour().pop(); }
    @Override protected boolean isEqual(Task other) {
        return other instanceof ShearWoolActivityTask task && task.count == count;
    }
    @Override protected String toDebugString() { return "Shearing sheep for " + count + " wool"; }

    private static final class ShearOne extends AbstractDoToEntityTask {
        private final SheepEntity sheep;
        private boolean interacted;

        ShearOne(SheepEntity sheep) {
            super(0, -1, -1);
            this.sheep = sheep;
        }
        boolean succeeded() { return interacted && sheep.isSheared(); }
        boolean available() { return ready(sheep); }
        @Override protected boolean isSubEqual(AbstractDoToEntityTask other) {
            return other instanceof ShearOne task && task.sheep == sheep;
        }
        @Override protected Task onEntityInteract(Vibrato mod, Entity entity) {
            if (available() && mod.getSlotHandler().forceEquipItem(Items.SHEARS)) {
                mod.getController().interactEntity(mod.getPlayer(), sheep, Hand.MAIN_HAND);
                interacted = true;
            }
            return null;
        }
        @Override protected Optional<Entity> getEntityTarget(Vibrato mod) {
            return available() ? Optional.of(sheep) : Optional.empty();
        }
        @Override protected String toDebugString() { return "Shearing sheep"; }
    }
}
