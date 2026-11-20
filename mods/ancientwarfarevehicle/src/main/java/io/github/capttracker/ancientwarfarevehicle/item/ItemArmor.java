/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/item/ItemArmor.
 */
package io.github.capttracker.ancientwarfarevehicle.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A vehicle armor plate (not wearable armor). Linked to its {@code IVehicleArmor} by registry name ({@code ArmorRegistry}). */
public class ItemArmor extends Item {
    private final String armorName;

    public ItemArmor(Properties properties, String armorName) {
        super(properties);
        this.armorName = armorName;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ancientwarfarevehicle." + armorName + ".tooltip"));
    }
}
