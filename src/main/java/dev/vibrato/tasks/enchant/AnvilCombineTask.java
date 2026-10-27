package dev.vibrato.tasks.enchant;

import net.minecraft.item.Item;

/**
 * Combines a target with a book or repair material in an anvil, taking the result.
 */
public class AnvilCombineTask extends AbstractAnvilTask {

    public AnvilCombineTask(Item target, Item bookOrMaterial) {
        super(target, bookOrMaterial);
    }

    @Override
    protected boolean isSameAnvilJob(AbstractAnvilTask other) {
        return true;
    }

    @Override
    protected String toDebugString() {
        return "Combining " + _target.getTranslationKey() + " with " + _second.getTranslationKey() + " in anvil";
    }
}
