package dev.vibrato.tasks.combat;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.resources.CollectBlazeRodsTask;
import dev.vibrato.tasks.resources.KillAndLootTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

/**
 * Gets a number of one drop by killing the mob that carries it. Blaze rods go through the nether task;
 * everything else is a kill-and-loot of the mob. Which mob is {@link MobDropTable}'s call.
 */
public class FarmMobDropsTask extends Task {

    private final Item _drop;
    private final int _count;
    private final String _mob;
    private Task _delegate;

    public FarmMobDropsTask(Item drop, int count) {
        String id = Registries.ITEM.getId(drop).getPath();
        _mob = MobDropTable.mobFor(id).orElseThrow(() -> new IllegalArgumentException("No farmable mob drops " + id));
        _drop = drop;
        _count = count;
    }

    @Override
    protected void onStart(Vibrato mod) {

    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_delegate == null) {
            _delegate = createDelegate();
        }
        if (_delegate.isFinished(mod)) {
            return null;
        }
        setDebugState("Farming " + _count + " " + _drop.getTranslationKey() + " from " + _mob);
        return _delegate;
    }

    private Task createDelegate() {
        if (_mob.equals("blaze")) {
            return new CollectBlazeRodsTask(_count);
        }
        return new KillAndLootTask(mobClass(_mob), new ItemTarget(_drop, _count));
    }

    private static Class<? extends Entity> mobClass(String mob) {
        return switch (mob) {
            case "cow" -> CowEntity.class;
            case "pig" -> PigEntity.class;
            case "sheep" -> SheepEntity.class;
            case "chicken" -> ChickenEntity.class;
            case "rabbit" -> RabbitEntity.class;
            case "squid" -> SquidEntity.class;
            case "skeleton" -> SkeletonEntity.class;
            case "spider" -> SpiderEntity.class;
            case "creeper" -> CreeperEntity.class;
            case "enderman" -> EndermanEntity.class;
            case "slime" -> SlimeEntity.class;
            case "zombie" -> ZombieEntity.class;
            default -> throw new IllegalArgumentException("No entity class for mob " + mob);
        };
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _delegate != null && _delegate.isFinished(mod);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof FarmMobDropsTask task && task._drop == _drop && task._count == _count;
    }

    @Override
    protected String toDebugString() {
        return "Farm " + _count + " " + _drop.getTranslationKey();
    }
}
