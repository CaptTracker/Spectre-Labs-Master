/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/item/ItemAmmo.
 */
package io.github.capttracker.ancientwarfarevehicle.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * An ammunition item. It is linked to its {@code IAmmo} rule object by registry name only
 * ({@code AmmoRegistry.getAmmoForStack}). Ammo that is not available as an item (the two ball shots and the arrow, which
 * only exist inside other ammo or as NPC ammo) is registered but kept out of the creative tab and has no "can be fired from" line.
 */
public class ItemAmmo extends Item {
    private final String ammoName;
    private final boolean availableAsItem;

    public ItemAmmo(Properties properties, String ammoName, boolean availableAsItem) {
        super(properties);
        this.ammoName = ammoName;
        this.availableAsItem = availableAsItem;
    }

    /** Registry path of the ammo and of this item, for example {@code ammo_stone_shot_10}. */
    public String getAmmoName() {
        return ammoName;
    }

    public boolean isAvailableAsItem() {
        return availableAsItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ancientwarfarevehicle." + ammoName + ".tooltip"));
        if (availableAsItem) {
            tooltip.add(Component.translatable("item.ancientwarfarevehicle." + ammoName + ".tooltip_vehicle_list"));
        }
    }
}
