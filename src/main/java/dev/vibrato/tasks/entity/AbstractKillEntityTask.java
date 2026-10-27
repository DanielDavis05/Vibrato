package dev.vibrato.tasks.entity;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.tasks.combat.CriticalHitPolicy;
import baritone.api.utils.input.Input;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * Attacks an entity, but the target entity must be specified.
 */
public abstract class AbstractKillEntityTask extends AbstractDoToEntityTask {

    private static final double OTHER_FORCE_FIELD_RANGE = 2;
    private int _criticalJumpStarted = -1;

    // Not the "striking" distance, but the "ok we're close enough, lower our guard for other mobs and focus on this one" range.
    private static final double CONSIDER_COMBAT_RANGE = 10;

    private static final Item[] WEAPON_ITEMS = new Item[]{
            Items.NETHERITE_SWORD,
            Items.DIAMOND_SWORD,
            Items.IRON_SWORD,
            Items.STONE_SWORD,
            Items.WOODEN_SWORD,
            Items.GOLDEN_SWORD,
            Items.NETHERITE_AXE,
            Items.DIAMOND_AXE,
            Items.IRON_AXE,
            Items.STONE_AXE,
            Items.WOODEN_AXE,
            Items.GOLDEN_AXE
    };

    public AbstractKillEntityTask() {
        this(CONSIDER_COMBAT_RANGE, OTHER_FORCE_FIELD_RANGE);
    }

    public AbstractKillEntityTask(double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
        super(combatGuardLowerRange, combatGuardLowerFieldRadius);
    }

    public AbstractKillEntityTask(double maintainDistance, double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
        super(maintainDistance, combatGuardLowerRange, combatGuardLowerFieldRadius);
    }

    public static void equipWeapon(Vibrato mod) {
        for (Item item : WEAPON_ITEMS) {
            if (mod.getItemStorage().hasItem(item)) {
                mod.getSlotHandler().forceEquipItem(item);
                return;
            }
        }
    }

    private boolean safeCriticalJump(Vibrato mod) {
        var player = mod.getPlayer();
        if (mod.getClientBaritone().getCustomGoalProcess().isActive() || !player.isOnGround() || player.isTouchingWater() || player.isClimbing() || player.isSprinting()
                || player.getAbilities().flying || player.getVelocity().horizontalLengthSquared() > 0.0025) return false;
        BlockPos feet = player.getBlockPos();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockPos below = feet.add(x, -1, z);
            if (!mod.getWorld().getBlockState(below).isSolidBlock(mod.getWorld(), below)) return false;
            if (!mod.getWorld().getFluidState(feet.add(x, 0, z)).isEmpty()) return false;
        }
        return mod.getWorld().getBlockState(feet.up(2)).isAir();
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        _criticalJumpStarted = -1;
        super.onStop(mod, interruptTask);
    }

    @Override
    protected Task onEntityInteract(Vibrato mod, Entity entity) {
        float hitProg = mod.getPlayer().getAttackCooldownProgress(0);

        // Equip weapon
        equipWeapon(mod);
        if (hitProg >= 0.99) {
            var player = mod.getPlayer();
            int elapsed = _criticalJumpStarted < 0 ? -1 : player.age - _criticalJumpStarted;
            if (_criticalJumpStarted < 0 && safeCriticalJump(mod)) {
                _criticalJumpStarted = player.age;
                mod.getInputControls().tryPress(Input.JUMP);
                return null;
            }
            if (CriticalHitPolicy.waitForFall(elapsed, player.isOnGround(), player.fallDistance)) return null;
            _criticalJumpStarted = -1;
            mod.getControllerExtras().attack(entity);
        }
        return null;
    }
}
