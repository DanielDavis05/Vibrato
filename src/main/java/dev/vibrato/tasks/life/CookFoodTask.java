package dev.vibrato.tasks.life;

import dev.vibrato.Vibrato;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.container.SmeltInFurnaceTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.SmeltTarget;
import dev.vibrato.util.helpers.ItemHelper;

import net.minecraft.item.Item;

/**
 * Smelts raw food in a furnace until the inventory holds a number of cooked items. Eating is left to the food chain.
 */
public class CookFoodTask extends Task {

    private final Item _raw;
    private final Item _cooked;
    private final int _count;
    private SmeltInFurnaceTask _smelt;

    public CookFoodTask(Item raw, int count) {
        _raw = raw;
        _cooked = ItemHelper.getCookedFood(raw)
                .orElseThrow(() -> new IllegalArgumentException("Not a smeltable food: " + raw));
        _count = count;
    }

    @Override
    protected void onStart(Vibrato mod) {
    }

    @Override
    protected Task onTick(Vibrato mod) {
        int cookedHave = mod.getItemStorage().getItemCount(_cooked);
        if (cookedHave >= _count) {
            return null;
        }
        if (mod.getItemStorage().getItemCount(_raw) == 0) {
            setDebugState("Getting raw food to cook");
            return TaskCatalogue.getItemTask(_raw, _count - cookedHave);
        }
        if (_smelt == null || _smelt.isFinished(mod)) {
            _smelt = new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(_cooked, _count), new ItemTarget(_raw, _count)));
            _smelt.ignoreMaterials();
        }
        setDebugState("Cooking " + _raw.getTranslationKey());
        return _smelt;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return mod.getItemStorage().getItemCount(_cooked) >= _count;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof CookFoodTask task && task._raw == _raw && task._count == _count;
    }

    @Override
    protected String toDebugString() {
        return "Cooking " + _count + " x " + _raw.getTranslationKey();
    }
}
