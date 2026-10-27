package dev.vibrato.tasks.combat;

import dev.vibrato.Vibrato;
import dev.vibrato.util.helpers.BaritoneHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/** Entity lookups shared by the combat tasks. */
final class CombatTargets {

    private CombatTargets() {}

    /** The closest living, reachable entity of the given type within range. Works for any type, unlike the tracker. */
    static Optional<Entity> closest(Vibrato mod, EntityType<?> type, double range) {
        Vec3d pos = mod.getPlayer().getPos();
        Entity closest = null;
        double best = range * range;
        synchronized (BaritoneHelper.MINECRAFT_LOCK) {
            for (Entity entity : mod.getWorld().getEntities()) {
                if (entity.getType() != type || !entity.isAlive()) continue;
                double distance = entity.squaredDistanceTo(pos);
                if (distance < best && mod.getEntityTracker().isEntityReachable(entity)) {
                    best = distance;
                    closest = entity;
                }
            }
        }
        return Optional.ofNullable(closest);
    }

    /** The closest hostile that is angry at us, within range. */
    static Optional<Entity> closestHostile(Vibrato mod, double range) {
        Entity closest = null;
        double best = range * range;
        Vec3d pos = mod.getPlayer().getPos();
        for (Entity hostile : mod.getEntityTracker().getHostiles()) {
            double distance = hostile.squaredDistanceTo(pos);
            if (hostile.isAlive() && distance < best) {
                best = distance;
                closest = hostile;
            }
        }
        return Optional.ofNullable(closest);
    }

    static double healthFraction(Entity entity) {
        if (entity instanceof LivingEntity living && living.getMaxHealth() > 0) {
            return living.getHealth() / living.getMaxHealth();
        }
        return 1;
    }
}
