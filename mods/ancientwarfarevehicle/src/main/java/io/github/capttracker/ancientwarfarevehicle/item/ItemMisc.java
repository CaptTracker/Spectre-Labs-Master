/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/item/ItemMisc.
 */
package io.github.capttracker.ancientwarfarevehicle.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** The 22 crafting ingredients of the module: 7 ammo materials and 15 vehicle components. Only a tooltip line, no behaviour. */
public class ItemMisc extends Item {
    private final VehicleItemType itemType;

    public ItemMisc(Properties properties, VehicleItemType itemType) {
        super(properties);
        this.itemType = itemType;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.ancientwarfarevehicle." + itemType.getItemTypeString()));
    }

    public enum VehicleItemType {
        AMMO_MATERIAL("ammo_material"),
        VEHICLE_COMPONENT("vehicle_component");

        private final String itemType;

        VehicleItemType(String itemType) {
            this.itemType = itemType;
        }

        public String getItemTypeString() {
            return itemType;
        }
    }
}
