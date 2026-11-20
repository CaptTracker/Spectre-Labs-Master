/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/inventory/AmmoStackHandler.
 */
package io.github.capttracker.ancientwarfarevehicle.inventory;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** The ammo bay: accepts only the ammo valid for the vehicle's type; recounts the ammo when it changes. */
public class AmmoStackHandler extends ItemStackHandler {
    private final VehicleBase vehicle;

    public AmmoStackHandler(VehicleBase vehicle, int size) {
        super(size);
        this.vehicle = vehicle;
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (!vehicle.level().isClientSide) {
            vehicle.ammoHelper.updateAmmoCounts();
        }
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return AmmoRegistry.getAmmoForStack(stack).map(ammo -> vehicle.vehicleType.isAmmoValidForInventory(ammo)).orElse(false);
    }
}
