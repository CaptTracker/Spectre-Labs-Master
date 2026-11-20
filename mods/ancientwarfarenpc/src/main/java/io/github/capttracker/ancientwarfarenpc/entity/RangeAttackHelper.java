/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/RangeAttackHelper.
 */
package io.github.capttracker.ancientwarfarenpc.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Fires arrows for the archer NPCs with the distance-compensated speed of the original. */
public final class RangeAttackHelper {
    private RangeAttackHelper() {}

    /**
     * Speed factor (range) calculations by Funwayguy in the original.
     *
     * <p>Power, Punch and Flame of the main-hand weapon: 1.12.2 added the damage, the knockback and the fire by hand. In 1.21.1
     * those enchantments are effects that read the weapon the arrow was fired from, so the weapon is passed to the arrow and the
     * projectile-spawned effects (Flame) are run like {@code ProjectileWeaponItem.shoot} does.
     */
    public static void doRangedAttack(LivingEntity attacker, LivingEntity target, float force, float inaccuracy) {
        double targetDist = Math.sqrt(attacker.distanceToSqr(target.getX() + (target.getX() - target.xOld), target.getBoundingBox().minY,
                target.getZ() + (target.getZ() - target.zOld)));
        float speedFactor = (float) ((0.00013 * (targetDist) * (targetDist)) + (0.02 * targetDist) + 1.25);

        ItemStack weapon = attacker.getMainHandItem();
        Arrow arrow = new Arrow(attacker.level(), attacker, new ItemStack(Items.ARROW), weapon.isEmpty() ? null : weapon.copy());
        double d0 = target.getX() - attacker.getX();
        double d1 = target.getBoundingBox().minY + (double) (target.getBbHeight() / 3.0F) - arrow.getY();
        double d2 = target.getZ() - attacker.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        arrow.shoot(d0, d1 + d3 * 0.20000000298023224D, d2, speedFactor, inaccuracy);

        arrow.setBaseDamage(force * 2.0D + attacker.getRandom().nextGaussian() * 0.25D);

        if (attacker.level() instanceof ServerLevel serverLevel && !weapon.isEmpty()) {
            EnchantmentHelper.onProjectileSpawned(serverLevel, weapon, arrow, item -> attacker.onEquippedItemBroken(item, EquipmentSlot.MAINHAND));
        }

        attacker.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (attacker.getRandom().nextFloat() * 0.4F + 0.8F));
        attacker.level().addFreshEntity(arrow);
    }
}
