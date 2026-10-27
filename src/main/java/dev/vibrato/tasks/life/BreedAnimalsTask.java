package dev.vibrato.tasks.life;

import dev.vibrato.Vibrato;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.entity.AbstractDoToEntityTask;
import dev.vibrato.tasks.movement.IdleTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Breeds a number of pairs of one farm animal: feeds two adults, waits for the baby, repeats. Food is fetched when
 * the bot has none of the animal's breeding items.
 */
public class BreedAnimalsTask extends Task {

    private final EntityType<?> _type;
    private final String _name;
    private final int _pairs;
    private final FeedTask _feed;
    private int _pairsDone;
    private int _babiesSeen;

    /** @throws IllegalArgumentException if {@code animal} is not in {@link BreedingFood} */
    public BreedAnimalsTask(String animal, int pairs) {
        if (!BreedingFood.canBreed(animal)) {
            throw new IllegalArgumentException("Cannot breed " + animal);
        }
        _name = animal;
        _type = Registries.ENTITY_TYPE.get(Identifier.of("minecraft", animal));
        _pairs = pairs;
        _feed = new FeedTask(_type);
    }

    @Override
    protected void onStart(Vibrato mod) {
        _pairsDone = 0;
        _babiesSeen = countBabies(mod);
    }

    @Override
    protected Task onTick(Vibrato mod) {
        int babies = countBabies(mod);
        if (babies > _babiesSeen) {
            _pairsDone += babies - _babiesSeen;
        }
        _babiesSeen = babies;

        Set<String> owned = BreedingFood.foodsFor(_name).stream()
                .filter(id -> mod.getItemStorage().hasItem(item(id)))
                .collect(Collectors.toSet());
        Optional<String> food = BreedingFood.firstOwned(_name, owned);
        if (food.isEmpty()) {
            String preferred = BreedingFood.foodsFor(_name).get(0);
            setDebugState("Getting " + preferred + " for " + _name);
            return TaskCatalogue.getItemTask(item(preferred), 2 * _pairs);
        }
        _feed.setFood(item(food.get()));

        long inLove = countInLove(mod);
        if (inLove >= 2) {
            setDebugState("Waiting for a " + _name + " baby");
            return new IdleTask();
        }
        if (countAdults(mod) < 2) {
            setDebugState("Looking for " + _name + " to breed");
            return new TimeoutWanderTask();
        }
        setDebugState("Feeding " + _name);
        return _feed;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _pairsDone >= _pairs;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof BreedAnimalsTask task && task._type == _type && task._pairs == _pairs;
    }

    @Override
    protected String toDebugString() {
        return "Breeding " + _pairs + " pair(s) of " + _name + " (" + _pairsDone + " done)";
    }

    private int countBabies(Vibrato mod) {
        return (int) mod.getEntityTracker().getTrackedEntities(AnimalEntity.class).stream()
                .filter(animal -> animal.getType() == _type && animal.isBaby())
                .count();
    }

    private long countAdults(Vibrato mod) {
        return mod.getEntityTracker().getTrackedEntities(AnimalEntity.class).stream()
                .filter(animal -> animal.getType() == _type && !animal.isBaby())
                .count();
    }

    private long countInLove(Vibrato mod) {
        return mod.getEntityTracker().getTrackedEntities(AnimalEntity.class).stream()
                .filter(animal -> animal.getType() == _type && !animal.isBaby() && animal.isInLove())
                .count();
    }

    private static Item item(String id) {
        return Registries.ITEM.get(Identifier.of("minecraft", id));
    }

    /** Walks to the nearest adult that is not in love yet and offers it the breeding food. */
    private static class FeedTask extends AbstractDoToEntityTask {

        private final EntityType<?> _type;
        private Item _food;

        FeedTask(EntityType<?> type) {
            _type = type;
        }

        void setFood(Item food) {
            _food = food;
        }

        @Override
        protected boolean isSubEqual(AbstractDoToEntityTask other) {
            return other instanceof FeedTask task && task._type == _type;
        }

        @Override
        protected Task onEntityInteract(Vibrato mod, Entity entity) {
            if (_food == null || !mod.getItemStorage().hasItem(_food)) {
                return null;
            }
            if (mod.getSlotHandler().forceEquipItem(_food)) {
                mod.getController().interactEntity(mod.getPlayer(), entity, Hand.MAIN_HAND);
            }
            return null;
        }

        @Override
        protected Optional<Entity> getEntityTarget(Vibrato mod) {
            return mod.getEntityTracker().getClosestEntity(mod.getPlayer().getPos(),
                    entity -> entity.getType() == _type && entity instanceof AnimalEntity animal
                            && !animal.isBaby() && !animal.isInLove(),
                    AnimalEntity.class);
        }

        @Override
        protected String toDebugString() {
            return "Feeding animals";
        }
    }
}
