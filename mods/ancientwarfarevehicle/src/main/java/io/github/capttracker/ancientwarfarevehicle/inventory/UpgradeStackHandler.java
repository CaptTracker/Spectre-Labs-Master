/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/inventory/UpgradeStackHandler.
 */
package io.github.capttracker.ancientwarfarevehicle.inventory;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** The upgrade bay: one item per slot, only upgrades valid for the vehicle's type. */
public class UpgradeStackHandler extends ItemStackHandler {
    private final VehicleBase vehicle;

    public UpgradeStackHandler(VehicleBase vehicle, int size) {
        super(size);
        this.vehicle = vehicle;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return UpgradeRegistry.getUpgrade(stack).map(upgrade -> vehicle.vehicleType.isUpgradeValid(upgrade)).orElse(false);
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
