/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/inventory/VehicleInventory.
 */
package io.github.capttracker.ancientwarfarevehicle.inventory;

import io.github.capttracker.ancientwarfarevehicle.armors.IVehicleArmor;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleAmmoEntry;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;
import io.github.capttracker.ancientwarfarevehicle.upgrades.IVehicleUpgradeType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * The four inventories of a vehicle: upgrades, ammo, armor and storage. Sized by the vehicle's type whenever
 * {@code setVehicleType} runs. Clients never read the contents; they keep only the name lists and ammo counts.
 */
public class VehicleInventory {
    private static final String INVENTORY_TAG = "inventory";
    private final VehicleBase vehicle;

    public ItemStackHandler upgradeInventory;
    public ItemStackHandler ammoInventory;
    public ItemStackHandler armorInventory;
    public ItemStackHandler storageInventory;

    public VehicleInventory(VehicleBase vehicle) {
        this.vehicle = vehicle;
        // empty until the vehicle gets its type, so that nothing sees a null handler
        setInventorySizes(0, 0, 0, 0);
    }

    public void setInventorySizes(int upgrade, int ammo, int armor, int storage) {
        this.ammoInventory = new AmmoStackHandler(vehicle, ammo);
        this.armorInventory = new ArmorStackHandler(vehicle, armor);
        this.storageInventory = new ItemStackHandler(storage);
        this.upgradeInventory = new UpgradeStackHandler(vehicle, upgrade);
    }

    public void writeToNBT(CompoundTag commonTag, HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.put("upgradeInventory", this.upgradeInventory.serializeNBT(provider));
        tag.put("ammoInventory", this.ammoInventory.serializeNBT(provider));
        tag.put("storageInventory", this.storageInventory.serializeNBT(provider));
        tag.put("armorInventory", this.armorInventory.serializeNBT(provider));
        commonTag.put(INVENTORY_TAG, tag);
    }

    public void readFromNBT(CompoundTag commonTag, HolderLookup.Provider provider) {
        if (!commonTag.contains(INVENTORY_TAG)) {
            return;
        }
        CompoundTag tag = commonTag.getCompound(INVENTORY_TAG);
        this.upgradeInventory.deserializeNBT(provider, tag.getCompound("upgradeInventory"));
        this.ammoInventory.deserializeNBT(provider, tag.getCompound("ammoInventory"));
        this.storageInventory.deserializeNBT(provider, tag.getCompound("storageInventory"));
        this.armorInventory.deserializeNBT(provider, tag.getCompound("armorInventory"));
    }

    public List<IVehicleArmor> getInventoryArmor() {
        List<IVehicleArmor> armors = new ArrayList<>();
        for (int i = 0; i < this.armorInventory.getSlots(); i++) {
            ArmorRegistry.getArmorForStack(this.armorInventory.getStackInSlot(i)).ifPresent(armors::add);
        }
        return armors;
    }

    public List<IVehicleUpgradeType> getInventoryUpgrades() {
        List<IVehicleUpgradeType> upgrades = new ArrayList<>();
        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            UpgradeRegistry.getUpgrade(this.upgradeInventory.getStackInSlot(i)).ifPresent(upgrades::add);
        }
        return upgrades;
    }

    /** The ammo in the ammo bay, totalled by type. */
    public List<VehicleAmmoEntry> getAmmoCounts() {
        List<VehicleAmmoEntry> counts = new ArrayList<>();
        for (int i = 0; i < this.ammoInventory.getSlots(); i++) {
            ItemStack stack = this.ammoInventory.getStackInSlot(i);
            AmmoRegistry.getAmmoForStack(stack).ifPresent(ammo -> {
                for (VehicleAmmoEntry entry : counts) {
                    if (entry.baseAmmoType == ammo) {
                        entry.ammoCount += stack.getCount();
                        return;
                    }
                }
                VehicleAmmoEntry entry = new VehicleAmmoEntry(ammo);
                entry.ammoCount += stack.getCount();
                counts.add(entry);
            });
        }
        return counts;
    }
}
