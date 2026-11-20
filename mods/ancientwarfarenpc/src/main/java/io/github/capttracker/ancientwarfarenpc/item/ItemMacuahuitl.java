/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemMacuahuitl.
 */
package io.github.capttracker.ancientwarfarenpc.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** The macuahuitl: an iron sword with 100 less durability, a dark purple name and repaired with obsidian. */
public class ItemMacuahuitl extends SwordItem {
    public ItemMacuahuitl(Properties properties, Tier tier) {
        super(tier, properties.attributes(createAttributes(tier, 3, -2.4F)));
    }

    /** The tier's durability minus 100 (the tiered item constructor resets the durability of the properties to the tier's). */
    @Override
    public int getMaxDamage(ItemStack stack) {
        return Math.max(1, getTier().getUses() - 100);
    }

    /** 1.12.2 had an own rarity with the colour dark purple; the vanilla rarities have no such colour, so the name is coloured. */
    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(ChatFormatting.DARK_PURPLE);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ancientwarfarenpc.macuahuitl.tooltip"));
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.is(Items.OBSIDIAN) || super.isValidRepairItem(toRepair, repair);
    }
}
