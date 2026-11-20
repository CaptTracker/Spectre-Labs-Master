/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeCannon.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.entity.materials.VehicleMaterial;
import io.github.capttracker.ancientwarfarevehicle.entity.varhelpers.CannonVarHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;
import net.minecraft.resources.ResourceLocation;

/** Base of the three cannon types (pitch aimed, iron material). */
public abstract class VehicleTypeCannon extends VehicleType {
    public VehicleTypeCannon(int typeNum) {
        super(typeNum);
        configName = "cannon_base";
        baseHealth = VehicleSettings.vehicleCannonHealth;
        vehicleMaterial = VehicleMaterial.materialIron;
        materialCount = 5;
        maxMissileWeight = 10.f;
        validAmmoTypes.add(AmmoRegistry.ammoIronShot5);
        validAmmoTypes.add(AmmoRegistry.ammoIronShot10);
        validAmmoTypes.add(AmmoRegistry.ammoGrapeShot5);
        validAmmoTypes.add(AmmoRegistry.ammoGrapeShot10);
        validAmmoTypes.add(AmmoRegistry.ammoCanisterShot5);
        validAmmoTypes.add(AmmoRegistry.ammoCanisterShot10);
        if (VehicleSettings.oversizeAmmoEnabled) {
            validAmmoTypes.add(AmmoRegistry.ammoIronShot15);
            validAmmoTypes.add(AmmoRegistry.ammoIronShot25);
            validAmmoTypes.add(AmmoRegistry.ammoGrapeShot15);
            validAmmoTypes.add(AmmoRegistry.ammoGrapeShot25);
            validAmmoTypes.add(AmmoRegistry.ammoCanisterShot15);
            validAmmoTypes.add(AmmoRegistry.ammoCanisterShot25);
        }
        ammoBySoldierRank.put(0, AmmoRegistry.ammoIronShot5);
        ammoBySoldierRank.put(1, AmmoRegistry.ammoIronShot5);
        ammoBySoldierRank.put(2, AmmoRegistry.ammoIronShot5);
        validUpgrades.add(UpgradeRegistry.pitchDownUpgrade);
        validUpgrades.add(UpgradeRegistry.pitchUpUpgrade);
        validUpgrades.add(UpgradeRegistry.pitchExtUpgrade);
        validUpgrades.add(UpgradeRegistry.powerUpgrade);
        validUpgrades.add(UpgradeRegistry.reloadUpgrade);
        validUpgrades.add(UpgradeRegistry.aimUpgrade);
        validArmors.add(ArmorRegistry.armorStone);
        validArmors.add(ArmorRegistry.armorIron);
        validArmors.add(ArmorRegistry.armorObsidian);
        turretVerticalOffset = 11.5f * 0.0625f;
        storageBaySize = 0;
        accuracy = 0.98f;
        drivable = true;
        baseForwardSpeed = 0.f;
        baseStrafeSpeed = .75f;
        basePitchMax = 15;
        basePitchMin = -15;
        mountable = true;
        combatEngine = true;
        pitchAdjustable = true;
        powerAdjustable = false;
        yawAdjustable = false;
        baseMissileVelocityMax = 42.f;
        width = 2;
        height = 2;
        armorBaySize = 3;
        upgradeBaySize = 3;
        ammoBaySize = 6;
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("cannon", level);
    }

    @Override
    public VehicleFiringVarsHelper getFiringVarsHelper(VehicleBase veh) {
        return new CannonVarHelper(veh);
    }
}
