package dev.vibrato.tasks.construction.compound;

import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;

/** Only durable stone-tier or stronger tools satisfy portal approach preparation. */
public final class PortalToolPolicy {
    static boolean needsTool(boolean usable, boolean frameComplete) { return !usable && !frameComplete; }

    public static boolean usable(ItemStack stack, int saverThreshold) {
        return usable(!stack.isEmpty() && stack.isSuitableFor(Blocks.IRON_ORE.getDefaultState()),
                stack.isDamageable(), stack.getDamage(), stack.getMaxDamage(), saverThreshold);
    }

    static boolean usable(boolean suitable, boolean damageable, int damage, int maxDamage, int saverThreshold) {
        return suitable && (!damageable || damage + Math.max(0, saverThreshold) < maxDamage);
    }
}
