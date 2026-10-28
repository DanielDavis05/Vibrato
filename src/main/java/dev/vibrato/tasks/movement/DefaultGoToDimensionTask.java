package dev.vibrato.tasks.movement;

import dev.vibrato.Vibrato;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.construction.compound.PortalToolPolicy;
import dev.vibrato.util.helpers.ItemHelper;
import net.minecraft.item.Items;
import dev.vibrato.tasks.construction.compound.ConstructNetherPortalBucketTask;
import dev.vibrato.tasks.construction.compound.ConstructNetherPortalObsidianTask;
import dev.vibrato.tasks.resources.CollectFoodTask;
import dev.vibrato.util.helpers.StorageHelper;
import net.minecraft.client.network.ClientPlayerEntity;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.tasksystem.IAllowsAutonomousFood;
import dev.vibrato.util.Dimension;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Some generic tasks require us to go to the nether/overworld/end.
 * <p>
 * The user should be able to specify how this should be done in settings
 * (ex, craft a new portal from scratch or check particular portal areas first or highway or whatever)
 */
public class DefaultGoToDimensionTask extends Task implements IAllowsAutonomousFood {

    private final Dimension _target;
    private final NetherFoodPlan _foodPlan = new NetherFoodPlan();
    private final NetherExpeditionPlan _expedition = new NetherExpeditionPlan();
    private int _stonePickaxeGoal;
    private int _stoneSwordGoal;
    private ClientPlayerEntity _foodPlayer;
    private Task _foodTask;
    // Cached to keep build properties alive if this task pauses/resumes.
    private final Task _cachedNetherBucketConstructionTask = new ConstructNetherPortalBucketTask();

    public DefaultGoToDimensionTask(Dimension target) {
        _target = target;
    }

    @Override
    protected void onStart(Vibrato mod) {
        mod.getBlockTracker().trackBlock(Blocks.NETHER_PORTAL);
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_foodPlayer != mod.getPlayer()) {
            _foodPlayer = mod.getPlayer();
            _foodPlan.reset();
            _expedition.reset();
            _stonePickaxeGoal = 0;
            _stoneSwordGoal = 0;
            _foodTask = null;
        }
        if (WorldHelper.getCurrentDimension() == _target) return null;

        switch (_target) {
            case OVERWORLD:
                switch (WorldHelper.getCurrentDimension()) {
                    case NETHER:
                        return goToOverworldFromNetherTask(mod);
                    case END:
                        return goToOverworldFromEndTask(mod);
                }
                break;
            case NETHER:
                switch (WorldHelper.getCurrentDimension()) {
                    case OVERWORLD:
                        return goToNetherFromOverworldTask(mod);
                    case END:
                        // First go to the overworld
                        return goToOverworldFromEndTask(mod);
                }
                break;
            case END:
                switch (WorldHelper.getCurrentDimension()) {
                    case NETHER:
                        // First go to the overworld
                        return goToOverworldFromNetherTask(mod);
                    case OVERWORLD:
                        return goToEndTask(mod);
                }
                break;
        }

        setDebugState(WorldHelper.getCurrentDimension() + " -> " + _target + " is NOT IMPLEMENTED YET!");
        return null;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(Blocks.NETHER_PORTAL);
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof DefaultGoToDimensionTask task) {
            return task._target == _target;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Going to dimension: " + _target + " (default version)";
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return WorldHelper.getCurrentDimension() == _target;
    }

    private Task goToOverworldFromNetherTask(Vibrato mod) {
        if (netherPortalIsClose(mod)) {
            setDebugState("Going to nether portal");
            return new EnterNetherPortalTask(Dimension.NETHER);
        }

        Optional<BlockPos> closest = mod.getMiscBlockTracker().getLastUsedNetherPortal(Dimension.NETHER);
        if (closest.isPresent()) {
            setDebugState("Going to last nether portal pos");
            return new GetToBlockTask(closest.get());
        }

        setDebugState("Constructing nether portal with obsidian");
        return new ConstructNetherPortalObsidianTask();
    }

    private Task goToOverworldFromEndTask(Vibrato mod) {
        setDebugState("TODO: Go to center portal (at 0,0). If it doesn't exist, kill ender dragon lol");
        return null;
    }

    private Task goToNetherFromOverworldTask(Vibrato mod) {
        if (netherPortalIsClose(mod)) {
            setDebugState("Going to nether portal");
            return new EnterNetherPortalTask(Dimension.NETHER);
        }
        boolean constructing = mod.getModSettings().getOverworldToNetherBehaviour() == OVERWORLD_TO_NETHER_BEHAVIOUR.BUILD_PORTAL_VANILLA;
        if (_foodPlan.shouldCollect(constructing, StorageHelper.calculateInventoryFoodScore(mod))) {
            setDebugState("Stocking food before underground portal preparation");
            if (_foodTask == null) _foodTask = new CollectFoodTask(NetherFoodPlan.FOOD_UNITS);
            return _foodTask;
        }
        if (constructing) {
            int threshold = mod.getClientBaritoneSettings().itemSaver.value
                    ? mod.getClientBaritoneSettings().itemSaverThreshold.value : 0;
            int usableTools = (int) mod.getPlayer().getInventory().main.stream()
                    .filter(stack -> PortalToolPolicy.usable(stack, threshold)).count();
            int logs = mod.getPlayer().getInventory().main.stream()
                    .filter(stack -> java.util.Arrays.asList(ItemHelper.LOG).contains(stack.getItem()))
                    .mapToInt(stack -> stack.getCount()).sum();
            boolean usableWeapon = mod.getPlayer().getInventory().main.stream()
                    .anyMatch(stack -> NetherWeaponPolicy.usable(stack, threshold));
            switch (_expedition.update(logs, usableTools, usableWeapon)) {
                case WOOD -> {
                    setDebugState("Stocking expedition wood before portal preparation");
                    return TaskCatalogue.getItemTask("log", NetherExpeditionPlan.LOGS);
                }
                case TOOLS -> {
                    int physicalStone = mod.getPlayer().getInventory().main.stream()
                            .filter(stack -> stack.isOf(Items.STONE_PICKAXE))
                            .mapToInt(stack -> stack.getCount()).sum();
                    _stonePickaxeGoal = NetherExpeditionPlan.stoneGoal(_stonePickaxeGoal, physicalStone, usableTools);
                    setDebugState("Preparing spare expedition pickaxes");
                    return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, _stonePickaxeGoal);
                }
                case WEAPON -> {
                    int physicalSwords = mod.getPlayer().getInventory().main.stream()
                            .filter(stack -> stack.isOf(Items.STONE_SWORD))
                            .mapToInt(stack -> stack.getCount()).sum();
                    _stoneSwordGoal = NetherExpeditionPlan.weaponGoal(_stoneSwordGoal, physicalSwords, usableWeapon);
                    setDebugState("Preparing an expedition sword");
                    return TaskCatalogue.getItemTask(Items.STONE_SWORD, _stoneSwordGoal);
                }
                case COMPLETE -> { _stonePickaxeGoal = 0; _stoneSwordGoal = 0; }
            }
        }
        setDebugState(constructing ? "Preparing and constructing Nether portal" : "Travelling to home base");
        return switch (mod.getModSettings().getOverworldToNetherBehaviour()) {
            case BUILD_PORTAL_VANILLA -> _cachedNetherBucketConstructionTask;
            case GO_TO_HOME_BASE -> new GetToBlockTask(mod.getModSettings().getHomeBasePosition());
        };
    }

    private Task goToEndTask(Vibrato mod) {
        // Keep in mind that getting to the end requires going to the nether first.
        setDebugState("TODO: Get to End, Same as BeatMinecraft");
        return null;
    }

    private boolean netherPortalIsClose(Vibrato mod) {
        if (mod.getBlockTracker().anyFound(Blocks.NETHER_PORTAL)) {
            Optional<BlockPos> closest = mod.getBlockTracker().getNearestTracking(mod.getPlayer().getPos(), Blocks.NETHER_PORTAL);
            return closest.isPresent() && closest.get().isWithinDistance(mod.getPlayer().getPos(), 2000);
        }
        return false;
    }

    public enum OVERWORLD_TO_NETHER_BEHAVIOUR {
        BUILD_PORTAL_VANILLA,
        GO_TO_HOME_BASE
    }
}
