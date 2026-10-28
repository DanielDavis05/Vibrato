package dev.vibrato.tasks.movement;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Weapons the normal delayed aura can equip with at least stone-sword strength. */
final class NetherWeaponPolicy {
    static boolean usable(ItemStack stack, int saverThreshold) {
        boolean sword = stack.isOf(Items.STONE_SWORD) || stack.isOf(Items.IRON_SWORD)
                || stack.isOf(Items.DIAMOND_SWORD) || stack.isOf(Items.NETHERITE_SWORD);
        return sword && durable(stack.getDamage(), stack.getMaxDamage(), saverThreshold);
    }
    static boolean durable(int damage, int maximum, int saverThreshold) {
        return damage + Math.max(0, saverThreshold) < maximum;
    }
}
