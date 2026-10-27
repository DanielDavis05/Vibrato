package dev.vibrato.tasks.combat;

import dev.vibrato.Vibrato;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.movement.PickupDroppedItemTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.LookHelper;
import dev.vibrato.util.helpers.ProjectileHelper;
import dev.vibrato.util.helpers.WorldHelper;
import dev.vibrato.util.time.TimerGame;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

/**
 * Shoots the nearest mob of a type with a bow: draws for a full charge, leads the target with the
 * ballistic solution, releases, and repeats. Afterwards picks the arrows back up. Bounded by shots and time.
 */
public class BowFightTask extends Task {

    private static final int FULL_DRAW_TICKS = 20;
    private static final double ARROW_SPEED = 3.0;
    private static final int MAX_SHOTS = 10;
    private static final double RANGE = 40;
    private static final double FIGHT_SECONDS = 60;
    private static final double COLLECT_SECONDS = 30;

    private final EntityType<?> _type;
    private final TimerGame _fightLimit = new TimerGame(FIGHT_SECONDS);
    private final TimerGame _collectLimit = new TimerGame(COLLECT_SECONDS);
    private int _shots;
    private int _draw;
    private boolean _drawing;
    private boolean _fightOver;
    private boolean _done;

    public BowFightTask(EntityType<?> type) {
        _type = type;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _shots = 0;
        _draw = 0;
        _drawing = false;
        _fightOver = false;
        _done = false;
        _fightLimit.reset();
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_done) return null;
        if (_fightOver) return collectArrows(mod);
        if (_shots >= MAX_SHOTS || _fightLimit.elapsed()) {
            return endFight(mod);
        }
        if (!mod.getItemStorage().hasItem(Items.BOW)) {
            setDebugState("Crafting a bow");
            return TaskCatalogue.getItemTask("bow", 1);
        }
        if (!mod.getItemStorage().hasItem(Items.ARROW)) {
            if (_shots > 0) return endFight(mod);
            setDebugState("Crafting arrows");
            return TaskCatalogue.getItemTask("arrow", 16);
        }
        if (!mod.getSlotHandler().forceEquipItem(Items.BOW)) {
            return endFight(mod);
        }

        Entity target = CombatTargets.closest(mod, _type, RANGE).orElse(null);
        if (target == null) {
            release(mod);
            setDebugState("No " + _type.getTranslationKey() + " in range");
            return null;
        }

        Rotation aim = aimAt(mod, target);
        LookHelper.lookAt(mod, aim);
        if (!_drawing) {
            mod.getInputControls().hold(Input.CLICK_RIGHT);
            _drawing = true;
            _draw = 0;
        }
        _draw++;
        if (_draw >= FULL_DRAW_TICKS && LookHelper.isLookingAt(mod, aim)) {
            release(mod);
            _shots++;
        }
        setDebugState("Shot " + _shots + " of " + MAX_SHOTS);
        return null;
    }

    /**
     * Ballistic pitch for a full-draw arrow, with the target's velocity folded in: the flight time depends on the
     * pitch, so the lead point is refined a few times.
     */
    static Rotation aimAt(Vibrato mod, Entity target) {
        Vec3d start = LookHelper.getCameraPos(mod.getPlayer());
        Vec3d center = target.getPos().add(0, target.getHeight() / 2, 0);
        Vec3d predicted = center;
        double pitch = 0;
        for (int i = 0; i < 3; i++) {
            double flat = WorldHelper.distanceXZ(start, predicted);
            pitch = ProjectileHelper.calculateAnglesForSimpleProjectileMotion(
                    start.y - predicted.y, flat, ARROW_SPEED, ProjectileHelper.ARROW_GRAVITY_ACCEL)[0];
            double flight = flat / (ARROW_SPEED * Math.cos(Math.toRadians(pitch)));
            predicted = center.add(target.getVelocity().multiply(flight));
        }
        float yaw = LookHelper.getLookRotation(mod, predicted).getYaw();
        return new Rotation(yaw, (float) -pitch);
    }

    private Task endFight(Vibrato mod) {
        release(mod);
        _fightOver = true;
        _collectLimit.reset();
        return null;
    }

    private Task collectArrows(Vibrato mod) {
        if (_collectLimit.elapsed() || !mod.getEntityTracker().itemDropped(Items.ARROW)) {
            _done = true;
            return null;
        }
        setDebugState("Collecting arrows");
        return new PickupDroppedItemTask(Items.ARROW, 9999999);
    }

    private void release(Vibrato mod) {
        if (_drawing) {
            mod.getInputControls().release(Input.CLICK_RIGHT);
            _drawing = false;
        }
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        release(mod);
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _done;
    }

    @Override
    protected boolean isEqual(Task other) {
        // A finished fight must not be resumed; a new one replaces it.
        return other instanceof BowFightTask task && task._type == _type && !task._done;
    }

    @Override
    protected String toDebugString() {
        return "Bow fight with " + _type.getTranslationKey();
    }
}
