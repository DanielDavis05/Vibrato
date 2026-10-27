package dev.vibrato.tasks.combat;

import dev.vibrato.Vibrato;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.misc.EquipArmorTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.StorageHelper;

import java.util.Locale;

/**
 * Gets a full armor set, plus a sword, pickaxe and shield, for one material tier. Armor is equipped; the rest
 * is crafted or smelted through the catalogue. Netherite comes from the catalogue's smithing recipes.
 */
public class UpgradeGearTask extends Task {

    public enum GearTier {
        IRON,
        DIAMOND,
        NETHERITE;

        String prefix() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final GearTier _tier;
    private final Task _armor;
    private final ItemTarget[] _kit;
    private final Task _kitTask;

    public UpgradeGearTask(GearTier tier) {
        _tier = tier;
        String p = tier.prefix();
        _armor = new EquipArmorTask(
                new ItemTarget(p + "_helmet", 1),
                new ItemTarget(p + "_chestplate", 1),
                new ItemTarget(p + "_leggings", 1),
                new ItemTarget(p + "_boots", 1));
        _kit = new ItemTarget[]{
                new ItemTarget(p + "_sword", 1),
                new ItemTarget(p + "_pickaxe", 1),
                new ItemTarget("shield", 1)
        };
        _kitTask = TaskCatalogue.getSquashedItemTask(_kit);
    }

    @Override
    protected void onStart(Vibrato mod) {

    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (!_armor.isFinished(mod)) {
            setDebugState("Equipping " + _tier.prefix() + " armor");
            return _armor;
        }
        if (!StorageHelper.itemTargetsMetInventory(mod, _kit)) {
            setDebugState("Making " + _tier.prefix() + " tools and shield");
            return _kitTask;
        }
        return null;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _armor.isFinished(mod) && StorageHelper.itemTargetsMetInventory(mod, _kit);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof UpgradeGearTask task && task._tier == _tier;
    }

    @Override
    protected String toDebugString() {
        return "Upgrading gear to " + _tier.prefix();
    }
}
