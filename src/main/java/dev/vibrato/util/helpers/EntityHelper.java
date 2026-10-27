package dev.vibrato.util.helpers;

import dev.vibrato.Vibrato;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.DamageTypeTags;

/**
 * Helper functions to interpret entity state
 */
public class EntityHelper {

    public static final double ENTITY_GRAVITY = 0.08; // per second

    public static boolean isAngryAtPlayer(Vibrato mod, Entity mob) {
        boolean hostile = isGenerallyHostileToPlayer(mod, mob);
        if (mob instanceof LivingEntity entity) {
            return hostile && entity.canSee(mod.getPlayer());
        }
        return hostile;
    }

    public static boolean isGenerallyHostileToPlayer(Vibrato mod, Entity hostile) {
        // TODO: Ignore on Peaceful difficulty.
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        // NOTE: These do not work.
        if (hostile instanceof EndermanEntity enderman) {
            return enderman.isAngryAt(player) && enderman.isAngry();
        }
        if (hostile instanceof PiglinEntity) {
            // Angry if we're not wearing gold
            return !StorageHelper.isArmorEquipped(mod, ItemHelper.GOLDEN_ARMORS);
        }
        if (hostile instanceof ZombifiedPiglinEntity zombie) {
            // Will ALWAYS be false.
            return zombie.hasAngerTime() && zombie.isAngryAt(player);
        }
        return !isTradingPiglin(hostile);
    }

    public static boolean isTradingPiglin(Entity entity) {
        if (entity instanceof PiglinEntity pig) {
            for (ItemStack stack : pig.getHandItems()) {
                if (stack.getItem().equals(Items.GOLD_INGOT)) {
                    // We're trading with this one, ignore it.
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Calculate the resulting damage dealt to a player as a result of some damage.
     * If this player were to receive this damage, the player's health will be subtracted by the resulting value.
     */
    public static double calculateResultingPlayerDamage(PlayerEntity player, DamageSource source, double damageAmount) {
        // Copied logic from `LivingEntity.applyDamage` (1.21)

        if (player.isInvulnerableTo(source))
            return 0;

        // Armor Base
        if (!source.isIn(DamageTypeTags.BYPASSES_ARMOR)) {
            damageAmount = DamageUtil.getDamageLeft(player, (float) damageAmount, source, (float) player.getArmor(), (float) player.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS));
        }

        // Enchantments & Potions
        if (!source.isIn(DamageTypeTags.BYPASSES_EFFECTS)) {
            int k;
            if (player.hasStatusEffect(StatusEffects.RESISTANCE) && !source.isOf(DamageTypes.OUT_OF_WORLD)) {
                //noinspection ConstantConditions
                k = (player.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1) * 5;
                int j = 25 - k;
                double f = damageAmount * (double)j;
                double g = damageAmount;
                damageAmount = Math.max(f / 25.0F, 0.0F);
            }

            if (damageAmount <= 0.0) {
                damageAmount = 0.0;
            } else {
                k = getClientProtectionAmount(player, source);
                if (k > 0) {
                    damageAmount = DamageUtil.getInflictedDamage((float)damageAmount, (float)k);
                }
            }
        }

        // Absorption
        damageAmount = Math.max(damageAmount - player.getAbsorptionAmount(), 0.0F);
        return damageAmount;
    }

    /**
     * Client-side approximation of the armor protection enchantments.
     * {@link EnchantmentHelper#getProtectionAmount} now requires a ServerWorld, so we sum the levels ourselves
     * the same way the 1.18 enchantments did.
     */
    private static int getClientProtectionAmount(PlayerEntity player, DamageSource source) {
        if (source.isIn(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
            return 0;
        }
        RegistryWrapper.Impl<Enchantment> enchantments = player.getWorld().getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT);
        int total = 0;
        for (ItemStack stack : player.getArmorItems()) {
            total += EnchantmentHelper.getLevel(enchantments.getOrThrow(Enchantments.PROTECTION), stack);
            if (source.isIn(DamageTypeTags.IS_FIRE)) {
                total += EnchantmentHelper.getLevel(enchantments.getOrThrow(Enchantments.FIRE_PROTECTION), stack) * 2;
            }
            if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
                total += EnchantmentHelper.getLevel(enchantments.getOrThrow(Enchantments.BLAST_PROTECTION), stack) * 2;
            }
            if (source.isIn(DamageTypeTags.IS_PROJECTILE)) {
                total += EnchantmentHelper.getLevel(enchantments.getOrThrow(Enchantments.PROJECTILE_PROTECTION), stack) * 2;
            }
            if (source.isIn(DamageTypeTags.IS_FALL)) {
                total += EnchantmentHelper.getLevel(enchantments.getOrThrow(Enchantments.FEATHER_FALLING), stack) * 3;
            }
        }
        return total;
    }
}
