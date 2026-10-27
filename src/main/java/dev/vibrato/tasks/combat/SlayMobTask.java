package dev.vibrato.tasks.combat;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.combat.MobTactics.Engagement;
import dev.vibrato.tasks.combat.MobTactics.Snapshot;
import dev.vibrato.tasks.combat.ShieldGuardBehavior.Threat;
import dev.vibrato.tasks.movement.GetToEntityTask;
import dev.vibrato.tasks.movement.PickupDroppedItemTask;
import dev.vibrato.tasks.movement.RunAwayFromHostilesTask;
import dev.vibrato.tasks.movement.RunAwayFromPositionTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasks.slot.MoveItemToSlotFromInventoryTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.EntityHelper;
import dev.vibrato.util.helpers.LookHelper;
import dev.vibrato.util.slots.PlayerSlot;
import dev.vibrato.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HoglinEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Hunts one kind of hostile mob until it has killed the requested number, using {@link MobTactics} each tick.
 * Gives up after a time limit, or retreats and fails when health drops below 30%.
 */
public class SlayMobTask extends Task {

    private static final double LOW_HEALTH_FRACTION = 0.3;
    private static final double SEARCH_RANGE = 48;
    private static final double LOOT_RANGE = 12;
    private static final double CLEAR_RANGE = 8;
    private static final double RUN_AWAY_DISTANCE = 30;
    private static final double SIDE_STEP_DISTANCE = 6;
    private static final double STEP_BACK_DISTANCE = 2;
    private static final double BLAST_RANGE = 4;
    private static final double ARROW_RANGE = 18;
    private static final double CHARGE_RANGE = 4;
    private static final int ZIGZAG_TICKS = 12;

    private static final Item[] WEAPONS = new Item[]{
            Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.IRON_SWORD, Items.STONE_SWORD, Items.WOODEN_SWORD, Items.GOLDEN_SWORD,
            Items.NETHERITE_AXE, Items.DIAMOND_AXE, Items.IRON_AXE, Items.STONE_AXE, Items.WOODEN_AXE, Items.GOLDEN_AXE
    };

    private final EntityType<?> _type;
    private final MobKind _kind;
    private final int _count;
    private final TimerGame _timeLimit;
    private final ShieldGuardBehavior _shield = new ShieldGuardBehavior();
    private final TimeoutWanderTask _wander = new TimeoutWanderTask(10);
    private Entity _target;
    private boolean _struck;
    private int _kills;
    private boolean _failed;
    private Task _retreat;
    private boolean _zigzagLeft = true;
    private int _zigzagTicks;

    public SlayMobTask(EntityType<?> type, int count) {
        _type = type;
        _kind = kindOf(type);
        _count = count;
        _timeLimit = new TimerGame(timeLimitSeconds(count));
    }

    /** Scales with the count; the activity budget matches this so the director never cuts a task short. */
    public static int timeLimitSeconds(int count) {
        return 180 + 40 * count;
    }

    /** Whether the task gave up (time out or too hurt) rather than reaching its kill count. */
    public boolean failed() {
        return _failed;
    }

    static MobKind kindOf(EntityType<?> type) {
        if (type == EntityType.ZOMBIE) return MobKind.ZOMBIE;
        if (type == EntityType.SKELETON) return MobKind.SKELETON;
        if (type == EntityType.CREEPER) return MobKind.CREEPER;
        if (type == EntityType.SPIDER) return MobKind.SPIDER;
        if (type == EntityType.ENDERMAN) return MobKind.ENDERMAN;
        if (type == EntityType.WITCH) return MobKind.WITCH;
        if (type == EntityType.BLAZE) return MobKind.BLAZE;
        if (type == EntityType.PIGLIN) return MobKind.PIGLIN;
        if (type == EntityType.HOGLIN) return MobKind.HOGLIN;
        if (type == EntityType.DROWNED) return MobKind.DROWNED;
        if (type == EntityType.PHANTOM) return MobKind.PHANTOM;
        if (type == EntityType.SLIME) return MobKind.SLIME;
        return MobKind.GENERIC;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _timeLimit.reset();
        _kills = 0;
        _failed = false;
        _retreat = null;
        _target = null;
        _struck = false;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_failed) {
            setDebugState("Giving up on " + _kind);
            return _retreat;
        }
        if (_timeLimit.elapsed()) {
            _failed = true;
            setDebugState("Out of time");
            return null;
        }
        if (healthFraction(mod) < LOW_HEALTH_FRACTION) {
            setDebugState("Too hurt, retreating");
            return giveUp(mod);
        }
        if (mod.getFoodChain().isTryingToEat()) {
            // The food chain owns right-click while eating; attacking now would fight it.
            _shield.lower(mod);
            setDebugState("Eating");
            return null;
        }
        if (!ShieldGuardBehavior.hasShieldInOffHand() && mod.getItemStorage().hasItem(Items.SHIELD)) {
            return new MoveItemToSlotFromInventoryTask(new ItemTarget(Items.SHIELD, 1), PlayerSlot.OFFHAND_SLOT);
        }

        if (_target != null && !_target.isAlive()) {
            if (_struck) _kills++;
            _target = null;
            _struck = false;
            if (_kills >= _count) return null;
        }
        if (_target == null) {
            _target = CombatTargets.closest(mod, _type, SEARCH_RANGE).orElse(null);
        }
        if (_target == null) {
            Task loot = lootTask(mod);
            if (loot != null) return loot;
            setDebugState("Searching for " + _kind);
            return _wander;
        }

        mod.getMobDefenseChain().setTargetEntity(_target);
        Snapshot snapshot = snapshot(mod);
        Engagement engagement = MobTactics.decide(_kind, snapshot);
        Threat threat = threat(mod, snapshot.distance());
        _shield.update(mod, threat != Threat.NONE || engagement == Engagement.SHIELD_UP,
                engagement == Engagement.KEEP_DISTANCE_AND_SHOOT);

        return switch (engagement) {
            case APPROACH_AND_MELEE, AVOID_EYE_CONTACT -> melee(mod, engagement == Engagement.AVOID_EYE_CONTACT, false);
            case HIT_AND_BACK_OFF -> melee(mod, false, true);
            case CLOSE_DISTANCE_ZIGZAG -> zigzag(mod);
            case KEEP_DISTANCE_AND_SHOOT -> {
                setDebugState("Shooting at " + _kind);
                yield new BowFightTask(_type);
            }
            case SHIELD_UP -> {
                setDebugState("Holding shield against " + _kind);
                yield null;
            }
            case RETREAT -> giveUp(mod);
            case SIDE_STEP -> {
                setDebugState("Stepping away from blast");
                yield new RunAwayFromPositionTask(SIDE_STEP_DISTANCE, _target.getBlockPos());
            }
        };
    }

    /** Walks in, then strikes when the cooldown is ready and the crosshair (or, for endermen, the reach) allows it. */
    private Task melee(Vibrato mod, boolean avoidEyes, boolean stepBack) {
        double reach = mod.getModSettings().getEntityReachRange();
        if (_target.squaredDistanceTo(mod.getPlayer()) > (reach - 1) * (reach - 1)) {
            setDebugState("Approaching " + _kind);
            return new GetToEntityTask(_target, reach - 1);
        }
        equipWeapon(mod);
        aim(mod, avoidEyes);
        boolean facing = avoidEyes || LookHelper.raycast(mod.getPlayer(), _target, reach).getType() == HitResult.Type.ENTITY;
        if (facing && AttackTiming.shouldSwing(mod.getPlayer().getAttackCooldownProgress(0), _kind)) {
            mod.getControllerExtras().attack(_target);
            _struck = true;
            if (stepBack) {
                setDebugState("Hit and backing off");
                return new RunAwayFromPositionTask(STEP_BACK_DISTANCE, _target.getBlockPos());
            }
        }
        setDebugState("Striking " + _kind);
        return null;
    }

    /** Closes in while strafing left and right, so a skeleton cannot line up a clean shot. */
    private Task zigzag(Vibrato mod) {
        double reach = mod.getModSettings().getEntityReachRange();
        if (_target.squaredDistanceTo(mod.getPlayer()) <= (reach - 1) * (reach - 1)) {
            releaseStrafe(mod);
            return melee(mod, false, false);
        }
        if (++_zigzagTicks >= ZIGZAG_TICKS) {
            _zigzagTicks = 0;
            _zigzagLeft = !_zigzagLeft;
        }
        releaseStrafe(mod);
        mod.getInputControls().hold(_zigzagLeft ? Input.MOVE_LEFT : Input.MOVE_RIGHT);
        setDebugState("Zigzagging in on " + _kind);
        return new GetToEntityTask(_target, reach - 1);
    }

    private void aim(Vibrato mod, boolean avoidEyes) {
        Vec3d point = avoidEyes ? _target.getPos() : _target.getPos().add(0, _target.getHeight() / 2, 0);
        LookHelper.lookAt(mod, point);
    }

    private Task giveUp(Vibrato mod) {
        _failed = true;
        _retreat = new RunAwayFromHostilesTask(RUN_AWAY_DISTANCE);
        return _retreat;
    }

    private void equipWeapon(Vibrato mod) {
        mod.getSlotHandler().forceEquipItem(WEAPONS);
    }

    private Snapshot snapshot(Vibrato mod) {
        ClientPlayerEntity player = mod.getPlayer();
        double distance = player.distanceTo(_target);
        boolean hasBow = mod.getItemStorage().hasItem(Items.BOW) && mod.getItemStorage().hasItem(Items.ARROW);
        return new Snapshot(distance, player.getHealth(), ShieldGuardBehavior.hasShieldInOffHand(), hasBow,
                CombatTargets.healthFraction(_target), isCharging(_target, distance), player.isOnGround(), player.isTouchingWater());
    }

    private static boolean isCharging(Entity target, double distance) {
        if (target instanceof CreeperEntity creeper) {
            return creeper.getClientFuseTime(1) > 0.001f;
        }
        return target instanceof HoglinEntity && distance < CHARGE_RANGE;
    }

    /** What is about to hit us, from the mobs around us. Decision is in {@link ShieldGuardBehavior#classify}. */
    private Threat threat(Vibrato mod, double distance) {
        ClientPlayerEntity player = mod.getPlayer();
        boolean arrowLine = false;
        for (SkeletonEntity skeleton : mod.getEntityTracker().getTrackedEntities(SkeletonEntity.class)) {
            if (skeleton.isInRange(player, ARROW_RANGE) && LookHelper.seesPlayer(skeleton, player, ARROW_RANGE)
                    && EntityHelper.isAngryAtPlayer(mod, skeleton)) {
                arrowLine = true;
                break;
            }
        }
        boolean blast = false;
        for (CreeperEntity creeper : mod.getEntityTracker().getTrackedEntities(CreeperEntity.class)) {
            if (creeper.getClientFuseTime(1) > 0.001f && creeper.isInRange(player, BLAST_RANGE)) {
                blast = true;
                break;
            }
        }
        boolean charge = false;
        for (HoglinEntity hoglin : mod.getEntityTracker().getTrackedEntities(HoglinEntity.class)) {
            if (hoglin.isInRange(player, CHARGE_RANGE) && EntityHelper.isAngryAtPlayer(mod, hoglin)) {
                charge = true;
                break;
            }
        }
        return ShieldGuardBehavior.classify(arrowLine, blast, charge);
    }

    /** Loot dropped items once no hostile is close, so we never stop to pick things up mid-fight. */
    private Task lootTask(Vibrato mod) {
        if (CombatTargets.closestHostile(mod, CLEAR_RANGE).isPresent()) return null;
        List<Item> items = new ArrayList<>();
        for (ItemEntity drop : mod.getEntityTracker().getDroppedItems()) {
            if (drop.isInRange(mod.getPlayer(), LOOT_RANGE) && !items.contains(drop.getStack().getItem())) {
                items.add(drop.getStack().getItem());
            }
        }
        if (items.isEmpty()) return null;
        setDebugState("Looting drops");
        return new PickupDroppedItemTask(new ItemTarget(items.toArray(Item[]::new), 9999999), true);
    }

    private static double healthFraction(Vibrato mod) {
        ClientPlayerEntity player = mod.getPlayer();
        return player.getHealth() / player.getMaxHealth();
    }

    private void releaseStrafe(Vibrato mod) {
        mod.getInputControls().release(Input.MOVE_LEFT);
        mod.getInputControls().release(Input.MOVE_RIGHT);
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _kills >= _count || (_failed && (_retreat == null || _retreat.isFinished(mod)));
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        _shield.lower(mod);
        releaseStrafe(mod);
        mod.getMobDefenseChain().resetTargetEntity();
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof SlayMobTask task) {
            return task._type == _type && task._count == _count;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Slay " + _count + " x " + _type.getTranslationKey();
    }
}
