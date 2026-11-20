/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/helpers/VehicleUpgradeHelper.
 */
package io.github.capttracker.ancientwarfarevehicle.helpers;

import io.github.capttracker.ancientwarfarevehicle.armors.IVehicleArmor;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;
import io.github.capttracker.ancientwarfarevehicle.upgrades.IVehicleUpgradeType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Keeps the installed upgrades and armor of a vehicle (derived from the inventories on the server, from the
 * broadcast name lists on clients) and applies their effects to the vehicle's current stats.
 */
public class VehicleUpgradeHelper implements INBTSerializable<CompoundTag> {
    /** Currently installed upgrades, applied one after the other; multiple upgrades may have cumulative effects. */
    private List<IVehicleUpgradeType> upgrades = new ArrayList<>();
    private List<IVehicleArmor> installedArmor = new ArrayList<>();

    /** All upgrades and armor that are valid for this vehicle. */
    private final List<IVehicleUpgradeType> validUpgrades = new ArrayList<>();
    private final List<IVehicleArmor> validArmorTypes = new ArrayList<>();
    private final VehicleBase vehicle;

    public VehicleUpgradeHelper(VehicleBase vehicle) {
        this.vehicle = vehicle;
    }

    /** SERVER ONLY: re-reads both inventories, tells the tracking clients and recomputes the stats. */
    public void updateUpgrades() {
        if (vehicle.level().isClientSide) {
            return;
        }
        upgrades = vehicle.inventory.getInventoryUpgrades();
        installedArmor = vehicle.inventory.getInventoryArmor();

        PacketDistributor.sendToPlayersTrackingEntity(vehicle,
                new VehicleEntityPayloads.UpgradeUpdate(vehicle.getId(), Arrays.asList(serializeUpgrades()), Arrays.asList(serializeInstalledArmors())));
        this.updateUpgradeStats();
    }

    public String[] serializeUpgrades() {
        String[] registryNames = new String[this.upgrades.size()];
        for (int i = 0; i < registryNames.length; i++) {
            registryNames[i] = this.upgrades.get(i).getRegistryName().toString();
        }
        return registryNames;
    }

    public String[] serializeInstalledArmors() {
        String[] armors = new String[installedArmor.size()];
        for (int i = 0; i < armors.length; i++) {
            armors[i] = installedArmor.get(i).getRegistryName().toString();
        }
        return armors;
    }

    /** CLIENT: receives the lists sent above and sets the upgrades and armor directly from the registries. */
    public void updateUpgrades(String[] armorRegistryNames, String[] upgradeRegistryNames) {
        deserializeInstalledArmor(armorRegistryNames);
        deserializeUpgrades(upgradeRegistryNames);
        updateUpgradeStats();
    }

    public List<IVehicleUpgradeType> getUpgrades() {
        return upgrades;
    }

    private void deserializeInstalledArmor(String[] armorRegistryNames) {
        installedArmor = new ArrayList<>();
        for (String armorRegistryName : armorRegistryNames) {
            ResourceLocation id = ResourceLocation.tryParse(armorRegistryName);
            if (id != null) {
                ArmorRegistry.getArmorType(id).ifPresent(installedArmor::add);
            }
        }
    }

    private void deserializeUpgrades(String[] upgradeRegistryNames) {
        upgrades = new ArrayList<>();
        for (String upgradeRegistryName : upgradeRegistryNames) {
            ResourceLocation id = ResourceLocation.tryParse(upgradeRegistryName);
            if (id != null) {
                UpgradeRegistry.getUpgrade(id).ifPresent(upgrades::add);
            }
        }
    }

    /**
     * Resets the stats to the base stats, then applies each upgrade in turn (multiple of the same upgrade are
     * cumulative) and each armor.
     */
    public void updateUpgradeStats() {
        vehicle.resetCurrentStats();
        for (IVehicleUpgradeType upgrade : this.upgrades) {
            upgrade.applyVehicleEffects(vehicle);
        }
        for (IVehicleArmor armor : this.installedArmor) {
            vehicle.currentExplosionResist += armor.getExplosiveDamageReduction();
            vehicle.currentFireResist += armor.getFireDamageReduction();
            vehicle.currentGenericResist += armor.getGeneralDamageReduction();
            vehicle.currentWeight += armor.getArmorWeight();
        }
    }

    public void addValidArmor(IVehicleArmor armor) {
        if (!this.validArmorTypes.contains(armor)) {
            this.validArmorTypes.add(armor);
        }
    }

    public void addValidUpgrade(IVehicleUpgradeType upgrade) {
        if (!this.validUpgrades.contains(upgrade)) {
            this.validUpgrades.add(upgrade);
        }
    }

    /**
     * Damage after the armor resistance. Damage types are data driven now: the explosive and fire missiles of the
     * ammo slice must be in the damage type tags {@code minecraft:is_explosion} and {@code minecraft:is_fire}
     * (the explosive missile in the explosion tag; explosion is tested first, as in the original).
     */
    public float getScaledDamage(DamageSource src, float amt) {
        if (src.is(DamageTypeTags.IS_EXPLOSION)) {
            return amt * (1 - (vehicle.currentExplosionResist * 0.01f));
        } else if (src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypes.IN_FIRE) || src.is(DamageTypes.LAVA) || src.is(DamageTypes.ON_FIRE)) {
            return amt * (1 - (vehicle.currentFireResist * 0.01f));
        }
        return amt * (1 - (vehicle.currentGenericResist * 0.01f));
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ListTag upgradesNbt = new ListTag();
        for (String upgrade : serializeUpgrades()) {
            upgradesNbt.add(StringTag.valueOf(upgrade));
        }
        tag.put("upgrades", upgradesNbt);
        ListTag armorTypes = new ListTag();
        for (String armor : serializeInstalledArmors()) {
            armorTypes.add(StringTag.valueOf(armor));
        }
        tag.put("armors", armorTypes);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        deserializeUpgrades(readStrings(tag, "upgrades"));
        deserializeInstalledArmor(readStrings(tag, "armors"));
    }

    private static String[] readStrings(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = list.getString(i);
        }
        return names;
    }

    public boolean hasUpgrade(IVehicleUpgradeType upgrade) {
        return this.upgrades.contains(upgrade);
    }
}
