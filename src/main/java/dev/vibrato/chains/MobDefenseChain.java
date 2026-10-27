package dev.vibrato.chains;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;
import dev.vibrato.tasks.entity.KillEntityTask;
import dev.vibrato.tasks.combat.TransitCombatPolicy;
import dev.vibrato.tasks.movement.CustomBaritoneGoalTask;
import dev.vibrato.tasks.movement.DodgeProjectilesTask;
import dev.vibrato.tasks.movement.RunAwayFromCreepersTask;
import dev.vibrato.tasks.movement.RunAwayFromHostilesTask;
import dev.vibrato.tasksystem.TaskRunner;
import dev.vibrato.control.KillAura;
import dev.vibrato.util.helpers.BaritoneHelper;
import dev.vibrato.util.baritone.CachedProjectile;
import dev.vibrato.util.helpers.EntityHelper;
import dev.vibrato.util.helpers.LookHelper;
import dev.vibrato.util.helpers.ProjectileHelper;
import baritone.Baritone;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.DragonFireballEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

@SuppressWarnings("rawtypes")
public class MobDefenseChain extends SingleTaskChain {

    private static final double CREEPER_KEEP_DISTANCE = 10;
    private static final double ARROW_KEEP_DISTANCE_HORIZONTAL = 2;//4;
    private static final double ARROW_KEEP_DISTANCE_VERTICAL = 10;//15;

    private static final double DANGER_KEEP_DISTANCE = 15 * 2;

    private static final double SAFE_KEEP_DISTANCE = 8;
    // Kind of a silly solution
    public static Class[] HOSTILE_ANNOYING_CLASSES = new Class[]{SkeletonEntity.class, ZombieEntity.class, SpiderEntity.class, CaveSpiderEntity.class, WitchEntity.class, PiglinEntity.class, PiglinBruteEntity.class, HoglinEntity.class, ZoglinEntity.class, BlazeEntity.class, WitherSkeletonEntity.class, PillagerEntity.class, DrownedEntity.class};
    private final KillAura _killAura = new KillAura();
    private Entity _targetEntity;
    private Entity _transitTarget;
    private int _engagementStarted;
    private int _lastEngagementEnded = Integer.MIN_VALUE;
    private boolean _doingFunkyStuff = false;
    private boolean _wasPuttingOutFire = false;
    private CustomBaritoneGoalTask _runAwayTask;

    private float _cachedLastPriority;

    public MobDefenseChain(TaskRunner runner) {
        super(runner);
    }

    public static double getCreeperSafety(Vec3d pos, CreeperEntity creeper) {
        double distance = creeper.squaredDistanceTo(pos);
        float fuse = creeper.getClientFuseTime(1);

        // Not fusing.
        if (fuse <= 0.001f) return distance;
        return distance * 0.2; // less is WORSE
    }

    @Override
    public float getPriority(Vibrato mod) {
        _cachedLastPriority = getPriorityInner(mod);
        return _cachedLastPriority;
    }
    private float getPriorityInner(Vibrato mod) {
        if (!Vibrato.inGame()) {
            return Float.NEGATIVE_INFINITY;
        }

        if (!mod.getModSettings().isMobDefense()) {
            return Float.NEGATIVE_INFINITY;
        }

        // Apply avoidance if we're vulnerable, avoiding mobs if at all possible.
        // mod.getClientBaritoneSettings().avoidance.value = isVulnurable(mod);
        // Doing you a favor by disabling avoidance


        // Pause if we're not loaded into a world.
        if (!Vibrato.inGame()) return Float.NEGATIVE_INFINITY;

        // Put out fire if we're standing on one like an idiot
        BlockPos fireBlock = isInsideFireAndOnFire(mod);
        if (fireBlock != null) {
            putOutFire(mod, fireBlock);
        } else if (_wasPuttingOutFire) {
            // Stop putting stuff out if we no longer need to put out a fire.
            mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
            _wasPuttingOutFire = false;
        }

        if (prioritizeEating(mod)) {
            return Float.NEGATIVE_INFINITY;
        }

        // Force field
        doForceField(mod);


        // Tell baritone to avoid mobs if we're vulnurable.
        // Costly.
        //mod.getClientBaritoneSettings().avoidance.value = isVulnurable(mod);

        // Run away if a weird mob is close by.
        Optional<Entity> universallyDangerous = getUniversallyDangerousMob(mod);
        if (universallyDangerous.isPresent()) {
            _runAwayTask = new RunAwayFromHostilesTask(DANGER_KEEP_DISTANCE);
            setTask(_runAwayTask);
            return 70;
        }

        _doingFunkyStuff = false;
        // Run away from creepers
        CreeperEntity blowingUp = getClosestFusingCreeper(mod);
        if (blowingUp != null) {
            _doingFunkyStuff = true;
            //Debug.logMessage("RUNNING AWAY!");
            _runAwayTask = new RunAwayFromCreepersTask(CREEPER_KEEP_DISTANCE);
            setTask(_runAwayTask);
            return 50 + blowingUp.getClientFuseTime(1) * 50;
        }

        // Dodge projectiles
        if (!mod.getFoodChain().isTryingToEat() && mod.getModSettings().isDodgeProjectiles() && isProjectileClose(mod)) {
            _doingFunkyStuff = true;
            //Debug.logMessage("DODGING");
            _runAwayTask = null;
            setTask(new DodgeProjectilesTask(ARROW_KEEP_DISTANCE_HORIZONTAL, ARROW_KEEP_DISTANCE_VERTICAL));
            return 65;
        }

        // Dodge all mobs cause we boutta die son
        if (isInDanger(mod)) {
            _doingFunkyStuff = true;
            if (_targetEntity == null) {
                _runAwayTask = new RunAwayFromHostilesTask(DANGER_KEEP_DISTANCE);
                setTask(_runAwayTask);
                return 70;
            }
        }

        // A transit encounter is one target and a short commitment, not an open-ended hunt.
        int now = mod.getPlayer().age;
        if (_transitTarget != null && !TransitCombatPolicy.keepEngaging(
                _transitTarget.isAlive(), _transitTarget.squaredDistanceTo(mod.getPlayer()), now - _engagementStarted)) {
            _transitTarget = null;
            _lastEngagementEnded = now;
        }
        if (_transitTarget == null && !isVulnurable(mod) && mod.getModSettings().shouldDealWithAnnoyingHostiles()
                && TransitCombatPolicy.mayStart(now, _lastEngagementEnded)) {
            _transitTarget = mod.getEntityTracker().getHostiles().stream()
                    .filter(entity -> Arrays.stream(HOSTILE_ANNOYING_CLASSES).anyMatch(type -> type.isInstance(entity)))
                    .filter(entity -> !mod.getBehaviour().shouldExcludeFromForcefield(entity))
                    .filter(entity -> EntityHelper.isAngryAtPlayer(mod, entity))
                    .filter(entity -> TransitCombatPolicy.eligible(entity.isAlive(), entity.squaredDistanceTo(mod.getPlayer()),
                            LookHelper.cleanLineOfSight(mod.getPlayer(), entity.getPos().add(0, entity.getHeight() / 2, 0), 6)))
                    .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mod.getPlayer())))
                    .orElse(null);
            if (_transitTarget != null) _engagementStarted = now;
        }
        if (_transitTarget != null) {
            _runAwayTask = null;
            setTask(new KillEntityTask(_transitTarget));
            return 65;
        }


        // By default if we aren't "immediately" in danger but were running away, keep running away until we're good.
        if (_runAwayTask != null && !_runAwayTask.isFinished(mod)) {
            setTask(_runAwayTask);
            return _cachedLastPriority;
        }

        return 0;
    }

    private BlockPos isInsideFireAndOnFire(Vibrato mod) {
        boolean onFire = mod.getPlayer().isOnFire();
        if (!onFire) return null;
        BlockPos p = mod.getPlayer().getBlockPos();
        BlockPos[] toCheck = new BlockPos[]{
                p,
                p.add(1, 0, 0),
                p.add(1, 0, 1),
                p.add(1, 0, -1),
                p.add(0, 0, 1),
                p.add(0, 0, -1),
                p.add(-1, 0, 1),
                p.add(-1, 0, -1)
        };
        for (BlockPos check : toCheck) {
            Block b = mod.getWorld().getBlockState(check).getBlock();
            if (b instanceof AbstractFireBlock) {
                return check;
            }
        }
        return null;
    }

    private void putOutFire(Vibrato mod, BlockPos pos) {
        Baritone b = mod.getClientBaritone();
        IPlayerContext ctx = b.getPlayerContext();
        Optional<Rotation> reachable = RotationUtils.reachableOffset(ctx.player(), pos, new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), ctx.playerController().getBlockReachDistance(), false);
        if (reachable.isPresent()) {
            b.getLookBehavior().updateTarget(reachable.get(), true);
            if (ctx.isLookingAt(pos)) {
                _wasPuttingOutFire = true;
                b.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
            }
        }
    }

    private boolean prioritizeEating(Vibrato mod) {
        return mod.getFoodChain().needsToEatCritical(mod);
    }

    private void doForceField(Vibrato mod) {

        _killAura.tickStart(mod);

        // Hit all hostiles close to us.
        List<Entity> entities = mod.getEntityTracker().getCloseEntities();
        try {
            for (Entity entity : entities) {
                boolean shouldForce = false;
                if (mod.getBehaviour().shouldExcludeFromForcefield(entity)) continue;
                if (entity instanceof Monster) {
                    if (EntityHelper.isGenerallyHostileToPlayer(mod, entity)) {
                        if (entity.isInRange(mod.getPlayer(), 1.75) && LookHelper.seesPlayer(entity, mod.getPlayer(), 10)) {
                            shouldForce = true;
                        }
                    }
                } else if (entity instanceof FireballEntity) {
                    // Ghast ball
                    shouldForce = true;
                } else if (entity instanceof PlayerEntity player && mod.getBehaviour().shouldForceFieldPlayers()) {
                    if (!player.equals(mod.getPlayer())) {
                        String name = player.getName().getString();
                        if (!mod.getButler().isUserAuthorized(name)) {
                            shouldForce = true;
                        }
                    }
                }
                if (shouldForce) {
                    applyForceField(mod, entity);
                }
            }
        } catch (Exception e) {
            Debug.logWarning("Weird exception caught and ignored while doing force field.");
            e.printStackTrace();
        }

        _killAura.tickEnd(mod);
    }

    private void applyForceField(Vibrato mod, Entity entity) {
        if (_targetEntity != null && _targetEntity.equals(entity)) return;
        _killAura.applyAura(mod, entity);
    }

    private CreeperEntity getClosestFusingCreeper(Vibrato mod) {
        double worstSafety = Float.POSITIVE_INFINITY;
        CreeperEntity target = null;
        try {
            List<CreeperEntity> creepers = mod.getEntityTracker().getTrackedEntities(CreeperEntity.class);
            for (CreeperEntity creeper : creepers) {

                if (creeper == null) continue;
                if (creeper.getClientFuseTime(1) < 0.001) continue;

                // We want to pick the closest creeper, but FIRST pick creepers about to blow
                // At max fuse, the cost goes to basically zero.
                double safety = getCreeperSafety(mod.getPlayer().getPos(), creeper);
                if (safety < worstSafety) {
                    target = creeper;
                }
            }
        } catch (ConcurrentModificationException | ArrayIndexOutOfBoundsException | NullPointerException e) {
            // IDK why but these exceptions happen sometimes. It's extremely bizarre and I have no idea why.
            Debug.logWarning("Weird Exception caught and ignored while scanning for creepers: " + e.getMessage());
            return target;
        }
        return target;
    }

    private boolean isProjectileClose(Vibrato mod) {
        List<CachedProjectile> projectiles = mod.getEntityTracker().getProjectiles();

        try {
            for (CachedProjectile projectile : projectiles) {

                boolean isGhastBall = projectile.projectileType == FireballEntity.class;
                if (isGhastBall) {
                    // Ignore ghast balls
                    continue;
                }
                if (projectile.projectileType == DragonFireballEntity.class) {
                    // Ignore dragon fireballs
                    continue;
                }

                Vec3d expectedHit = ProjectileHelper.calculateArrowClosestApproach(projectile, mod.getPlayer());

                Vec3d delta = mod.getPlayer().getPos().subtract(expectedHit);

                //Debug.logMessage("EXPECTED HIT OFFSET: " + delta + " ( " + projectile.gravity + ")");

                double horizontalDistanceSq = delta.x * delta.x + delta.z * delta.z;
                double verticalDistance = Math.abs(delta.y);

                if (horizontalDistanceSq < ARROW_KEEP_DISTANCE_HORIZONTAL*ARROW_KEEP_DISTANCE_HORIZONTAL && verticalDistance < ARROW_KEEP_DISTANCE_VERTICAL) {
                    if (Boolean.getBoolean("vibrato.debugNavigation") && mod.getPlayer().age % 5 == 0) {
                        System.out.printf(java.util.Locale.ROOT,
                                "[NavTrace] projectile=%s pos=%s velocity=%s nearest=%s player=%s%n",
                                projectile.projectileType, projectile.position, projectile.velocity, expectedHit, mod.getPlayer().getPos());
                    }
                    return true;
                }
            }
        } catch (ConcurrentModificationException e) {
            Debug.logWarning("Weird exception caught and ignored while checking for nearby projectiles.");
        }
        return false;
    }

    private Optional<Entity> getUniversallyDangerousMob(Vibrato mod) {
        // Wither skeletons are dangerous because of the wither effect. Oof kinda obvious.
        // If we merely force field them, we will run into them and get the wither effect which will kill us.
        if (mod.getEntityTracker().entityFound(WitherSkeletonEntity.class)) {
            Optional<Entity> entity = mod.getEntityTracker().getClosestEntity(mod.getPlayer().getPos(), WitherSkeletonEntity.class);
            if (entity.isPresent()) {
                double range = SAFE_KEEP_DISTANCE - 2;
                if (entity.get().squaredDistanceTo(mod.getPlayer()) < range * range && EntityHelper.isAngryAtPlayer(mod, entity.get())) {
                    return entity;
                }
            }
        }
        // Hoglins are dangerous because we can't push them with the force field.
        // If we merely force field them and stand still our health will slowly be chipped away until we die
        if (mod.getEntityTracker().entityFound(HoglinEntity.class, ZoglinEntity.class)) {
            if (mod.getPlayer().getHealth() < 10) {
                Optional<Entity> entity = mod.getEntityTracker().getClosestEntity(mod.getPlayer().getPos(), HoglinEntity.class, ZoglinEntity.class);
                if (entity.isPresent()) {
                    double range = SAFE_KEEP_DISTANCE - 1;
                    if (entity.get().squaredDistanceTo(mod.getPlayer()) < range * range && EntityHelper.isAngryAtPlayer(mod, entity.get())) {
                        return entity;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private boolean isInDanger(Vibrato mod) {

        if (isVulnurable(mod)) {
            // If hostile mobs are nearby...
            try {
                ClientPlayerEntity player = mod.getPlayer();
                List<Entity> hostiles = mod.getEntityTracker().getHostiles();
                for (Entity entity : hostiles) {
                    // Ignore skeletons
                    if (entity instanceof SkeletonEntity) continue;
                    if (entity.isInRange(player, SAFE_KEEP_DISTANCE) && !mod.getBehaviour().shouldExcludeFromForcefield(entity) && EntityHelper.isAngryAtPlayer(mod, entity)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                Debug.logWarning("Weird multithread exception. Will fix later.");
            }
        }

        return false;
    }

    private boolean isVulnurable(Vibrato mod) {
        int armor = mod.getPlayer().getArmor();
        float health = mod.getPlayer().getHealth();
        if (armor <= 15 && health < 3) return true;
        if (armor < 10 && health < 10) return true;
        return armor < 5 && health < 18;
    }

    public void setTargetEntity(Entity entity) {
        _targetEntity = entity;
    }
    public void resetTargetEntity() {
        _targetEntity = null;
    }

    public void setForceFieldRange(double range) {
        _killAura.setRange(range);
    }

    public void resetForceField() {
        _killAura.setRange(Double.POSITIVE_INFINITY);
    }

    public boolean isDoingAcrobatics() {
        return _doingFunkyStuff;
    }

    @Override
    public boolean isActive() {
        // We're always checking for mobs
        return true;
    }

    @Override
    protected void onTaskFinish(Vibrato mod) {
        // Task is done, so I guess we move on?
    }

    @Override
    public String getName() {
        return "Mob Defense";
    }
}