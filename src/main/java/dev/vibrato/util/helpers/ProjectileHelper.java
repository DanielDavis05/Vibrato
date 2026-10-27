package dev.vibrato.util.helpers;

import dev.vibrato.Debug;
import dev.vibrato.util.baritone.CachedProjectile;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Projectile motion math functions
 */
public class ProjectileHelper {

    public static final double ARROW_GRAVITY_ACCEL = 0.05000000074505806;
    public static final double THROWN_ENTITY_GRAVITY_ACCEL = 0.03;

    public static boolean hasGravity(ProjectileEntity entity) {
        if (entity instanceof ExplosiveProjectileEntity) return false;
        return !entity.hasNoGravity();
    }

    /** How far ahead to consider a projectile, in game ticks. */
    public static final double DODGE_HORIZON_TICKS = 40;
    private static final double MIN_HORIZONTAL_SPEED_SQUARED = 1.0e-8;

    private static double horizontalApproachTime(Vec3d origin, Vec3d velocity, Vec3d target) {
        double speedSquared = velocity.x * velocity.x + velocity.z * velocity.z;
        if (speedSquared < MIN_HORIZONTAL_SPEED_SQUARED) return 0;
        double time = (velocity.x * (target.x - origin.x) + velocity.z * (target.z - origin.z)) / speedSquared;
        return Math.max(0, Math.min(DODGE_HORIZON_TICKS, time));
    }

    public static double getFlatDistanceSqr(double shootX, double shootZ, double velX, double velZ, double playerX, double playerZ) {
        Vec3d origin = new Vec3d(shootX, 0, shootZ);
        Vec3d velocity = new Vec3d(velX, 0, velZ);
        Vec3d target = new Vec3d(playerX, 0, playerZ);
        double time = horizontalApproachTime(origin, velocity, target);
        return origin.add(velocity.multiply(time)).squaredDistanceTo(target);
    }

    /** Predicts the nearest horizontal encounter on the future trajectory, bounded in time.
     * For nearly vertical shots, choose the time of nearest vertical approach instead. */
    public static Vec3d calculateArrowClosestApproach(Vec3d shootOrigin, Vec3d shootVelocity, double yGravity, Vec3d playerOrigin) {
        double time = horizontalApproachTime(shootOrigin, shootVelocity, playerOrigin);
        if (shootVelocity.x * shootVelocity.x + shootVelocity.z * shootVelocity.z < MIN_HORIZONTAL_SPEED_SQUARED) {
            double best = Math.abs(shootOrigin.y - playerOrigin.y);
            // Sampling half ticks handles vertical shots and avoids division by zero.
            for (double t = 0.5; t <= DODGE_HORIZON_TICKS; t += 0.5) {
                double distance = Math.abs(shootOrigin.y + shootVelocity.y * t - 0.5 * yGravity * t * t - playerOrigin.y);
                if (distance < best) {
                    best = distance;
                    time = t;
                }
            }
        }
        return new Vec3d(shootOrigin.x + shootVelocity.x * time,
                shootOrigin.y + shootVelocity.y * time - 0.5 * yGravity * time * time,
                shootOrigin.z + shootVelocity.z * time);
    }

    public static Vec3d calculateArrowClosestApproach(CachedProjectile projectile, Vec3d pos) {
        return calculateArrowClosestApproach(projectile.position, projectile.velocity, projectile.gravity, pos);
    }

    public static Vec3d calculateArrowClosestApproach(CachedProjectile projectile, ClientPlayerEntity player) {
        return calculateArrowClosestApproach(projectile, player.getPos());
    }

    public static double[] calculateAnglesForSimpleProjectileMotion(double launchHeight, double launchTargetDistance, double launchVelocity, double gravity) {
        // Thanks wikipedia https://en.wikipedia.org/wiki/Projectile_motion#Angle_%CE%B8_required_to_hit_coordinate_(x,_y)
        double v = launchVelocity,
              g = gravity,
              x = launchTargetDistance,
              y = -1 * launchHeight;
        double root = v*v*v*v - g * (g*x*x + 2*y*v*v);
        if (root < 0) {
            // Imaginary root means not enough power, return 45 as the best/furthest angle.
            Debug.logMessage("Not enough velocity, returning 45 degrees.");
            return new double[]{45, 45};
        }

        double tanTheta0 = (v*v + Math.sqrt(root)) / (g*x);
        double tanTheta1 = (v*v - Math.sqrt(root)) / (g*x);

        // Return the smaller + bigger calculated angles
        double[] angles = new double[]{Math.toDegrees(Math.atan(tanTheta0)), Math.toDegrees(Math.atan(tanTheta1))};
        return new double[] {Math.min(angles[0], angles[1]), Math.max(angles[0], angles[1])};
    }

    public static Vec3d getThrowOrigin(Entity entity) {
        // Minecraft Magic Number
        return entity.getPos().subtract(0, 0.1, 0);
    }

    // Unable to figure out how to extract multiple roots, this is too complicated for engineering major like me.
    @SuppressWarnings("UnnecessaryLocalVariable")
    @Deprecated
    private static double getNearestTimeOfShotProjectile(Vec3d shootOrigin, Vec3d shootVelocity, double yGravity, Vec3d playerOrigin) {
        // Formatted for equations and ease of writing. This is why I'm not minoring in math.
        Vec3d D = playerOrigin.subtract(shootOrigin);
        Vec3d V = shootVelocity;
        double g = yGravity;

        // Cubic terms
        double a = (g * g) / 2.0;
        double b = -(3.0 * g * V.y) / 2.0;
        double c = V.lengthSquared() + (g * V.y);
        double d = -1 * V.dotProduct(D);


        // Now that we have our cubic equation for SQUARED distance, find the "zero" points.
        // This will only happen when we reach either a local minimum or a local maximum.

        // Solution stuff, thanks https://math.vanderbilt.edu/schectex/courses/cubic/
        double p = -b / (3.0 * a);
        double q = (p * p * p) + (((b * c) - (3.0 * a * d)) / (6.0 * a * a));
        double r = c / (3.0 * a);

        double rootInner = (q * q) + Math.pow(r - p * p, 3);

        // Theoretically there could exist imaginary roots that will cancel themselves out, but that ought to be rare.
        // We will get an imaginary root somewhere so ignore it.
        if (rootInner < 0) return -1;

        rootInner = Math.sqrt(rootInner);


        double outerPreCubeLeft = q + rootInner;
        double outerPreCubeRight = q - rootInner;

        // THERE SHOULD BE UP TO 3!!! This will only find one and that can be completely wrong.
        return Math.pow(outerPreCubeLeft, 1.0 / 3.0) + Math.pow(outerPreCubeRight, 1.0 / 3.0) + p;
    }

}
