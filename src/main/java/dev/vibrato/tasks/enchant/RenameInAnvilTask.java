package dev.vibrato.tasks.enchant;

import net.minecraft.item.Item;
import net.minecraft.screen.AnvilScreenHandler;

/**
 * Renames an item at an anvil, the same way the anvil's name field does, and takes the renamed item.
 */
public class RenameInAnvilTask extends AbstractAnvilTask {

    private final String _name;

    public RenameInAnvilTask(Item item, String name) {
        super(item, null);
        _name = name;
    }

    @Override
    protected void onTargetPlaced(AnvilScreenHandler handler) {
        handler.setNewItemName(_name);
    }

    @Override
    protected boolean isSameAnvilJob(AbstractAnvilTask other) {
        return other instanceof RenameInAnvilTask task && task._name.equals(_name);
    }

    @Override
    protected String toDebugString() {
        return "Renaming " + _target.getTranslationKey() + " to " + _name;
    }
}
