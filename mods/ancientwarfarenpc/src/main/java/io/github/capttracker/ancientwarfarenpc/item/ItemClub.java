/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemClub.
 */
package io.github.capttracker.ancientwarfarenpc.item;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/**
 * The giant club: a long reach weapon that knocks its target further, disables shields and cannot be given the Knockback enchantment. Like the
 * original it does not wear out when it hits (1.12.2 replaced {@code hitEntity} without damaging the stack).
 */
public class ItemClub extends ItemExtendedReachWeapon {
    private static final double KNOCKBACK = 1;

    public ItemClub(Properties properties, Tier tier, double attackOffset, double attackSpeed, float reach) {
        super(properties, tier, attackOffset, attackSpeed, reach);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        Vec3 motion = target.getDeltaMovement();
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        double dx;
        double dz;
        if (horizontal > 1.0E-4D) {
            // 1.12.2: more velocity in the direction the target is moving
            dx = motion.x * KNOCKBACK * 0.6D / horizontal;
            dz = motion.z * KNOCKBACK * 0.6D / horizontal;
        } else {
            // 1.12.2 divided by zero here (NaN velocity) when the target stood still; push it away from the attacker instead
            Vec3 away = target.position().subtract(attacker.position());
            double length = Math.sqrt(away.x * away.x + away.z * away.z);
            dx = length > 1.0E-4D ? away.x / length * KNOCKBACK * 0.6D : 0;
            dz = length > 1.0E-4D ? away.z / length * KNOCKBACK * 0.6D : 0;
        }
        target.push(dx, 0.1D, dz);
        target.hurtMarked = true;
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // no durability loss, as in 1.12.2
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        return stored.keySet().stream().noneMatch(enchantment -> enchantment.is(Enchantments.KNOCKBACK));
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return !enchantment.is(Enchantments.KNOCKBACK) && super.supportsEnchantment(stack, enchantment);
    }

    @Override
    public boolean canDisableShield(ItemStack stack, ItemStack shield, LivingEntity entity, LivingEntity attacker) {
        return true;
    }
}
