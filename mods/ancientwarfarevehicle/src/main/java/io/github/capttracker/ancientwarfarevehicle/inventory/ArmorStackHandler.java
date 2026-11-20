/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/inventory/ArmorStackHandler.
 */
package io.github.capttracker.ancientwarfarevehicle.inventory;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** The armor bay: one item per slot, only armor valid for the vehicle's type. */
public class ArmorStackHandler extends ItemStackHandler {
    private final VehicleBase vehicle;

    public ArmorStackHandler(VehicleBase vehicle, int size) {
        super(size);
        this.vehicle = vehicle;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return ArmorRegistry.getArmorForStack(stack).map(armor -> vehicle.vehicleType.isArmorValid(armor)).orElse(false);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (!vehicle.level().isClientSide) {
            vehicle.upgradeHelper.updateUpgrades();
        }
    }
}
