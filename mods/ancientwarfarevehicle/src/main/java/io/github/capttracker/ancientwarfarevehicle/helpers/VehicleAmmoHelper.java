/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/helpers/VehicleAmmoHelper.
 */
package io.github.capttracker.ancientwarfarevehicle.helpers;

import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfarevehicle.entity.IVehicleCrew;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.missiles.IAmmo;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiFunction;

/**
 * Keeps the ammo types valid for the vehicle with their counts (mirrored from the ammo inventory) and the
 * currently selected ammo type.
 */
public class VehicleAmmoHelper implements INBTSerializable<CompoundTag> {
    private static final String CURRENT_AMMO_TYPE_TAG = "currentAmmoType";
    private final VehicleBase vehicle;

    @Nullable
    private ResourceLocation currentAmmoType = null;

    private final NavigableMap<ResourceLocation, VehicleAmmoEntry> ammoEntries = new TreeMap<>();

    public VehicleAmmoHelper(VehicleBase vehicle) {
        this.vehicle = vehicle;
    }

    public int getCountOf(IAmmo type) {
        VehicleAmmoEntry ammo = ammoEntries.get(type.getRegistryName());
        return ammo == null ? 0 : ammo.ammoCount;
    }

    /** SERVER ONLY: removes one round of the current ammo from the inventory and tells the tracking clients the new count. */
    void decreaseCurrentAmmo() {
        if (vehicle.level().isClientSide) {
            return;
        }
        if (currentAmmoType != null && ammoEntries.containsKey(currentAmmoType)) {
            int removed = InventoryTools.removeItems(vehicle.inventory.ammoInventory, new ItemStack(AmmoRegistry.getItem(currentAmmoType)), 1).getCount();
            VehicleAmmoEntry entry = this.ammoEntries.get(this.currentAmmoType);
            int origCount = entry.ammoCount;
            entry.ammoCount -= removed;
            if (entry.ammoCount < 0) {
                entry.ammoCount = 0;
            }
            if (entry.ammoCount != origCount) {
                PacketDistributor.sendToPlayersTrackingEntity(vehicle,
                        new VehicleEntityPayloads.SingleAmmoUpdate(vehicle.getId(), currentAmmoType.toString(), entry.ammoCount));
            }
        }
    }

    /**
     * The current EFFECTIVE ammo count (not the actual one): soldiers use this to fool the vehicle into firing
     * infinite rounds.
     *
     * <p>Obvious fix of the original, which tested {@code ownedSoldiersUseAmmo} here without the negation (the
     * option reads "makes player owned soldiers require ammo", and the ammo type below tests it negated):
     * owned siege engineers now get the unlimited count when the option is off.
     */
    public int getCurrentAmmoCount() {
        LivingEntity rider = vehicle.getFiringCrew();
        if (rider instanceof IVehicleCrew crew
                && (crew.isFactionSoldier() || !VehicleSettings.ownedSoldiersUseAmmo && crew.isOwnedSiegeEngineer())) {
            return 64;
        }
        if (currentAmmoType != null && ammoEntries.containsKey(currentAmmoType)) {
            return this.ammoEntries.get(currentAmmoType).ammoCount;
        }
        return 0;
    }

    /**
     * SERVER: makes sure the selected ammo has stock, switching to another type that has some (what a player does with the ammo keys; an NPC gunner has no keys).
     * Does not use the client path of {@link #setNextAmmo}, which sends a payload to the server.
     *
     * @return true if a round can be fired now
     */
    public boolean ensureAmmoAvailable() {
        if (vehicle.level().isClientSide) {
            return false;
        }
        if (getCurrentAmmoCount() > 0 && getCurrentAmmoType() != null) {
            return true;
        }
        for (Map.Entry<ResourceLocation, VehicleAmmoEntry> entry : ammoEntries.entrySet()) {
            if (entry.getValue().ammoCount > 0) {
                updateSelectedAmmo(entry.getKey().toString());
                return getCurrentAmmoCount() > 0 && getCurrentAmmoType() != null;
            }
        }
        return false;
    }

    boolean doesntUseAmmo() {
        return vehicle.vehicleType.getValidAmmoTypes().isEmpty();
    }

    public void addUseableAmmo(IAmmo ammo) {
        this.ammoEntries.put(ammo.getRegistryName(), new VehicleAmmoEntry(ammo));
    }

    public void setNextAmmo() {
        getAvailable(this::getHigherWrapped).ifPresent(this::setCurrentAmmo);
    }

    private <K, V> Map.Entry<K, V> getHigherWrapped(NavigableMap<K, V> map, K key) {
        Map.Entry<K, V> entry = map.higherEntry(key);
        return entry == null ? map.firstEntry() : entry;
    }

    private void setCurrentAmmo(ResourceLocation registryName) {
        currentAmmoType = registryName;
        handleClientAmmoSelection(registryName);
    }

    private Optional<ResourceLocation> getAvailable(
            BiFunction<NavigableMap<ResourceLocation, VehicleAmmoEntry>, ResourceLocation, Map.Entry<ResourceLocation, VehicleAmmoEntry>> getNextEntry) {
        if (currentAmmoType == null) {
            return Optional.empty();
        }
        Map.Entry<ResourceLocation, VehicleAmmoEntry> entry = getNextEntry.apply(ammoEntries, currentAmmoType);
        while (entry.getValue().ammoCount <= 0 && !currentAmmoType.equals(entry.getKey())) {
            entry = getNextEntry.apply(ammoEntries, entry.getKey());
        }
        return Optional.of(entry.getKey());
    }

    private <K, V> Map.Entry<K, V> getLowerWrapped(NavigableMap<K, V> map, K key) {
        Map.Entry<K, V> entry = map.lowerEntry(key);
        return entry == null ? map.lastEntry() : entry;
    }

    public void setPreviousAmmo() {
        getAvailable(this::getLowerWrapped).ifPresent(this::setCurrentAmmo);
    }

    /** CLIENT: tells the server which ammo the player selected. */
    public void handleClientAmmoSelection(ResourceLocation ammoRegistryName) {
        PacketDistributor.sendToServer(new VehicleEntityPayloads.AmmoSelect(vehicle.getId(), ammoRegistryName.toString()));
    }

    public void updateSelectedAmmo(String ammoRegistryName) {
        if (!ammoRegistryName.equals(currentAmmoType == null ? "" : currentAmmoType.toString())) {
            ResourceLocation selected = ResourceLocation.tryParse(ammoRegistryName);
            if (selected == null || !ammoEntries.containsKey(selected)) {
                // not an ammo type of this vehicle (a client may send any name)
                return;
            }
            this.currentAmmoType = selected;
            if (!vehicle.level().isClientSide) {
                PacketDistributor.sendToPlayersTrackingEntity(vehicle, new VehicleEntityPayloads.AmmoSelect(vehicle.getId(), ammoRegistryName));
            }
            float maxPower = vehicle.firingHelper.getAdjustedMaxMissileVelocity();
            if (!vehicle.canAimPower()) {
                vehicle.localLaunchPower = maxPower;
            } else if (vehicle.localLaunchPower > maxPower) {
                vehicle.localLaunchPower = maxPower;
                if (vehicle.level().isClientSide && vehicle.firingHelper.clientLaunchSpeed > maxPower) {
                    vehicle.firingHelper.clientLaunchSpeed = maxPower;
                }
            }
        }
    }

    public void updateAmmoCount(String registryName, int count) {
        ResourceLocation rl = ResourceLocation.tryParse(registryName);
        if (rl != null) {
            updateAmmoCount(rl, count);
        }
    }

    private void updateAmmoCount(ResourceLocation registryNameLocation, int count) {
        VehicleAmmoEntry entry = ammoEntries.get(registryNameLocation);
        if (entry != null) {
            entry.ammoCount = count;
        }
    }

    /** SERVER ONLY: recounts the ammo inventory and tells the tracking clients. */
    public void updateAmmoCounts() {
        if (vehicle.level().isClientSide) {
            return;
        }

        for (VehicleAmmoEntry ent : this.ammoEntries.values()) {
            ent.ammoCount = 0;
        }
        List<VehicleAmmoEntry> counts = vehicle.inventory.getAmmoCounts();

        for (VehicleAmmoEntry count : counts) {
            updateAmmoCount(count.baseAmmoType.getRegistryName(), count.ammoCount);
            if (currentAmmoType == null && count.ammoCount > 0) {
                updateSelectedAmmo(count.baseAmmoType.getRegistryName().toString());
            }
        }
        PacketDistributor.sendToPlayersTrackingEntity(vehicle, new VehicleEntityPayloads.AmmoUpdate(vehicle.getId(), serializeAmmo(new CompoundTag())));
    }

    /** The ammo to fire: the selected one, or for soldiers without stock the ammo of their rank. */
    @Nullable
    public IAmmo getCurrentAmmoType() {
        LivingEntity rider = vehicle.getFiringCrew();
        if (currentAmmoType != null && ammoEntries.containsKey(currentAmmoType)) {
            VehicleAmmoEntry entry = this.ammoEntries.get(currentAmmoType);
            if (entry != null && (!(rider instanceof IVehicleCrew) || entry.ammoCount > 0)) {
                return entry.baseAmmoType;
            }
        }
        if (rider instanceof IVehicleCrew crew
                && (crew.isFactionSoldier() || !VehicleSettings.ownedSoldiersUseAmmo && crew.isOwnedSiegeEngineer())) {
            return vehicle.vehicleType.getAmmoForSoldierRank(crew.getSoldierLevel());
        }
        return null;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (currentAmmoType != null) {
            tag.putString(CURRENT_AMMO_TYPE_TAG, currentAmmoType.toString());
        }
        serializeAmmo(tag);
        return tag;
    }

    private CompoundTag serializeAmmo(CompoundTag tag) {
        ListTag tagList = new ListTag();
        for (VehicleAmmoEntry ent : this.ammoEntries.values()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("type", ent.baseAmmoType.getRegistryName().toString());
            entryTag.putInt("count", ent.ammoCount);
            tagList.add(entryTag);
        }
        tag.put("list", tagList);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        for (VehicleAmmoEntry ent : this.ammoEntries.values()) {
            ent.ammoCount = 0;
        }
        if (tag.contains(CURRENT_AMMO_TYPE_TAG)) {
            currentAmmoType = ResourceLocation.tryParse(tag.getString(CURRENT_AMMO_TYPE_TAG));
        }
        deserializeAmmo(tag);
    }

    /** CLIENT: replaces all counts with the ones the server sent. */
    public void updateAmmo(CompoundTag tag) {
        for (VehicleAmmoEntry ent : this.ammoEntries.values()) {
            ent.ammoCount = 0;
        }
        deserializeAmmo(tag);
    }

    private void deserializeAmmo(CompoundTag tag) {
        ListTag ammo = tag.getList("list", Tag.TAG_COMPOUND);
        for (int i = 0; i < ammo.size(); i++) {
            CompoundTag entryTag = ammo.getCompound(i);
            updateAmmoCount(entryTag.getString("type"), entryTag.getInt("count"));
        }
    }
}
