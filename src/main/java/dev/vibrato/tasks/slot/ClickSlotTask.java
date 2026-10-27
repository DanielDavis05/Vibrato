package dev.vibrato.tasks.slot;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.slots.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class ClickSlotTask extends Task {

    private final Slot _slot;
    private final int _mouseButton;
    private final SlotActionType _type;

    private boolean _clicked = false;

    public ClickSlotTask(Slot slot, int mouseButton, SlotActionType type) {
        _slot = slot;
        _mouseButton = mouseButton;
        _type = type;
    }
    public ClickSlotTask(Slot slot, SlotActionType type) {
        this(slot, 0, type);
    }
    public ClickSlotTask(Slot slot, int mouseButton) {
        this(slot, mouseButton, SlotActionType.PICKUP);
    }
    public ClickSlotTask(Slot slot) {
        this(slot, SlotActionType.PICKUP);
    }

    @Override
    protected void onStart(Vibrato mod) {
        _clicked = false;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (mod.getSlotHandler().canDoSlotAction()) {
            mod.getSlotHandler().clickSlot(_slot, _mouseButton, _type);
            mod.getSlotHandler().registerSlotAction();
            _clicked = true;
        }
        return null;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task obj) {
        if (obj instanceof ClickSlotTask task) {
            return task._mouseButton == _mouseButton && task._type == _type && task._slot.equals(_slot);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Clicking " + _slot.toString();
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _clicked;
    }
}
