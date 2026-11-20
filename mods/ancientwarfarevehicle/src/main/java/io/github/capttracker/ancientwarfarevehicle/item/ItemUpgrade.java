/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/item/ItemUpgrade.
 */
package io.github.capttracker.ancientwarfarevehicle.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A vehicle upgrade. Linked to its {@code IVehicleUpgradeType} by registry name ({@code UpgradeRegistry}). */
public class ItemUpgrade extends Item {
    private final String upgradeName;

    public ItemUpgrade(Properties properties, String upgradeName) {
        super(properties);
        this.upgradeName = upgradeName;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ancientwarfarevehicle." + upgradeName + ".tooltip"));
        tooltip.add(Component.translatable("tooltip.ancientwarfarevehicle.vehicle_upgrade"));
    }
}
