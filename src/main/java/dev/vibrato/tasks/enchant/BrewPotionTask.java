package dev.vibrato.tasks.enchant;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.DoToClosestBlockTask;
import dev.vibrato.tasks.InteractWithBlockTask;
import dev.vibrato.tasks.container.DoStuffInContainerTask;
import dev.vibrato.tasks.enchant.PotionRecipes.Potion;
import dev.vibrato.tasks.slot.ClickSlotTask;
import dev.vibrato.tasks.slot.MoveItemToSlotFromInventoryTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.slots.BrewingStandSlot;
import dev.vibrato.util.time.TimerGame;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Brews a potion in a brewing stand, placing the stand if needed: fills three water bottles, adds fuel, then brews
 * each ingredient of the {@link Potion}'s chain in turn and takes the potions out.
 */
public class BrewPotionTask extends DoStuffInContainerTask {

    private static final int WATER_BOTTLES = 3;
    private static final BrewingStandSlot[] POTION_SLOTS = {
            BrewingStandSlot.LEFT_POTION, BrewingStandSlot.MIDDLE_POTION, BrewingStandSlot.RIGHT_POTION
    };

    private final Potion _potion;
    private final Task _fillBottles;
    private final TimerGame _stepTimer = new TimerGame(15);
    private int _step = 0;
    private boolean _started = false;
    private boolean _failed = false;
    private boolean _done = false;

    public BrewPotionTask(Potion potion) {
        super(Blocks.BREWING_STAND, new ItemTarget(Items.BREWING_STAND, 1));
        _potion = potion;
        _fillBottles = new DoToClosestBlockTask(pos -> new InteractWithBlockTask(new ItemTarget(Items.GLASS_BOTTLE, 1), pos), Blocks.WATER);
    }

    /** True when the stand could not finish the brew. */
    public boolean failed() {
        return _failed;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_done) {
            return null;
        }
        if (!isContainerOpen(mod)) {
            Task prerequisite = missingPrerequisite(mod);
            if (prerequisite != null) {
                return prerequisite;
            }
        }
        return super.onTick(mod);
    }

    /** A task that gets the next missing brewing input, or null when everything is in the inventory. */
    private Task missingPrerequisite(Vibrato mod) {
        int water = mod.getItemStorage().getItemCount(Items.POTION);
        if (water < WATER_BOTTLES) {
            int glass = mod.getItemStorage().getItemCount(Items.GLASS_BOTTLE);
            if (glass >= WATER_BOTTLES - water) {
                setDebugState("Filling water bottles");
                return _fillBottles;
            }
            return TaskCatalogue.getItemTask("glass_bottle", WATER_BOTTLES - water);
        }
        if (!mod.getItemStorage().hasItem(Items.BLAZE_POWDER)) {
            return TaskCatalogue.getItemTask("blaze_powder", 1);
        }
        for (String name : _potion.ingredients()) {
            if (!mod.getItemStorage().hasItem(ingredientItem(name))) {
                return TaskCatalogue.getItemTask(name, 1);
            }
        }
        return null;
    }

    @Override
    protected boolean isContainerOpen(Vibrato mod) {
        return mod.getPlayer().currentScreenHandler instanceof BrewingStandScreenHandler;
    }

    @Override
    protected Task containerSubTask(Vibrato mod) {
        BrewingStandScreenHandler handler = (BrewingStandScreenHandler) mod.getPlayer().currentScreenHandler;
        List<String> chain = _potion.ingredients();

        if (handler.getBrewTime() > 0) {
            _started = true;
            _stepTimer.reset();
            setDebugState("Brewing step " + (_step + 1) + " of " + chain.size());
            return null;
        }
        if (!StorageHelper.getItemStackInSlot(BrewingStandSlot.INGREDIENT).isEmpty()) {
            // The ingredient is in but the brew has not started yet.
            _started = true;
            if (_stepTimer.elapsed()) {
                return fail();
            }
            setDebugState("Waiting for brewing to start");
            return null;
        }
        if (_started) {
            _started = false;
            _step++;
            _stepTimer.reset();
        }
        if (_step >= chain.size()) {
            for (BrewingStandSlot slot : POTION_SLOTS) {
                if (!StorageHelper.getItemStackInSlot(slot).isEmpty()) {
                    setDebugState("Taking potions out");
                    return new ClickSlotTask(slot, SlotActionType.QUICK_MOVE);
                }
            }
            _done = true;
            setDebugState("Potions taken out");
            return null;
        }

        for (BrewingStandSlot slot : POTION_SLOTS) {
            if (StorageHelper.getItemStackInSlot(slot).isEmpty()) {
                setDebugState("Placing water bottle");
                return new MoveItemToSlotFromInventoryTask(new ItemTarget(Items.POTION, 1), slot);
            }
        }
        if (handler.getFuel() <= 0 && StorageHelper.getItemStackInSlot(BrewingStandSlot.FUEL).isEmpty()) {
            setDebugState("Adding blaze powder fuel");
            return new MoveItemToSlotFromInventoryTask(new ItemTarget(Items.BLAZE_POWDER, 1), BrewingStandSlot.FUEL);
        }
        setDebugState("Adding " + chain.get(_step));
        return new MoveItemToSlotFromInventoryTask(new ItemTarget(ingredientItem(chain.get(_step)), 1), BrewingStandSlot.INGREDIENT);
    }

    private Task fail() {
        _failed = true;
        _done = true;
        StorageHelper.closeScreen();
        return null;
    }

    private static Item ingredientItem(String name) {
        return Registries.ITEM.get(Identifier.of("minecraft", name));
    }

    @Override
    protected double getCostToMakeNew(Vibrato mod) {
        return 100;
    }

    @Override
    protected boolean isSubTaskEqual(DoStuffInContainerTask other) {
        return other instanceof BrewPotionTask task && task._potion == _potion;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _done;
    }

    @Override
    protected String toDebugString() {
        return "Brewing " + _potion.name().toLowerCase();
    }
}
