/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemIceSpear.
 */
package io.github.capttracker.ancientwarfarenpc.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;

/** The ice spear: a rare long reach weapon that slows (50 ticks) and weakens the mining speed (150 ticks) of what it hits. It does not wear out when it hits, as in 1.12.2. */
public class ItemIceSpear extends ItemExtendedReachWeapon {
    public ItemIceSpear(Properties properties, Tier tier, double attackOffset, double attackSpeed, float reach) {
        super(properties.rarity(Rarity.RARE), tier, attackOffset, attackSpeed, reach);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50), attacker);
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 150), attacker);
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // no durability loss, as in 1.12.2
    }
}
