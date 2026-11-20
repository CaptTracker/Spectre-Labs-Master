/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemShield.
 */
package io.github.capttracker.ancientwarfarenpc.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.Tier;

/**
 * The shields of the NPC module (5 tiers and 11 cosmetic ones with the tier of their material). They are vanilla shields: the block animation,
 * the use time of 72000 ticks and blocking come from {@link ShieldItem} (and its {@code SHIELD_BLOCK} item ability), the durability is the
 * item's. The shield gets no enchantment glint of its own, loses 2 durability when used to hit and is repaired with the tier's repair material.
 * The {@code blocking} model property is registered in {@code client/NpcItemPropertiesClient}.
 */
public class ItemShield extends ShieldItem {
    private final Tier tier;

    public ItemShield(Properties properties, Tier tier) {
        super(properties);
        this.tier = tier;
    }

    public Tier getTier() {
        return tier;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity attacked, LivingEntity attacker) {
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity attacked, LivingEntity attacker) {
        stack.hurtAndBreak(2, attacker, EquipmentSlot.MAINHAND);
    }

    /** 1.12.2: the material's repair item (ore dictionary match), else the default. */
    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return tier.getRepairIngredient().test(repair) || super.isValidRepairItem(toRepair, repair);
    }
}
