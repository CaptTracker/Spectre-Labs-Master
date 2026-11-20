/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeBallista.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSounds;
import io.github.capttracker.ancientwarfarevehicle.entity.materials.VehicleMaterial;
import io.github.capttracker.ancientwarfarevehicle.entity.varhelpers.BallistaVarHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;

/** Base of the four ballista types (pitch aimed). */
public abstract class VehicleTypeBallista extends VehicleType {
    public VehicleTypeBallista(int typeNum) {
        super(typeNum);
        configName = "ballista_base";
        vehicleMaterial = VehicleMaterial.materialWood;
        materialCount = 5;
        baseHealth = VehicleSettings.vehicleBallistaHealth;
        maxMissileWeight = 2.f;
        ShotAmmoLists.addBallistaBolts(validAmmoTypes);
        ammoBySoldierRank.put(0, AmmoRegistry.ammoBallistaBolt);
        ammoBySoldierRank.put(1, AmmoRegistry.ammoBallistaBolt);
        ammoBySoldierRank.put(2, AmmoRegistry.ammoBallistaBoltFlame);
        validUpgrades.add(UpgradeRegistry.pitchDownUpgrade);
        validUpgrades.add(UpgradeRegistry.pitchUpUpgrade);
        validUpgrades.add(UpgradeRegistry.pitchExtUpgrade);
        validUpgrades.add(UpgradeRegistry.powerUpgrade);
        validUpgrades.add(UpgradeRegistry.reloadUpgrade);
        validUpgrades.add(UpgradeRegistry.aimUpgrade);
        validArmors.add(ArmorRegistry.armorStone);
        validArmors.add(ArmorRegistry.armorObsidian);
        validArmors.add(ArmorRegistry.armorIron);
        storageBaySize = 0;
        accuracy = 0.98f;
        baseStrafeSpeed = 2.25f;
        baseForwardSpeed = 4.f * 0.05f;
        basePitchMax = 15;
        basePitchMin = -15;
        mountable = true;
        combatEngine = true;
        pitchAdjustable = true;
        powerAdjustable = false;
        // default values that are overridden by the ballista types
        baseMissileVelocityMax = 42.f; // stand versions should have a higher velocity, as should the fixed versions: the mobile turret has the worst of all
        width = 2;
        height = 2;
        armorBaySize = 3;
        upgradeBaySize = 3;
        ammoBaySize = 6;
        drivable = false; // adjust based on mobile or not
        yawAdjustable = false; // adjust based on turret or not
        turretRotationMax = 360.f; // adjust: mobile fixed (0), stand fixed (90), mobile or stand turret (360)
    }

    @Override
    public VehicleFiringVarsHelper getFiringVarsHelper(VehicleBase veh) {
        return new BallistaVarHelper(veh);
    }

    @Override
    public void playReloadSound(VehicleBase vehicleBase) {
        VehicleSounds.play(vehicleBase, VehicleSounds.BALLISTA_RELOAD, 1, 1);
    }

    @Override
    public void playFiringSound(VehicleBase vehicleBase) {
        VehicleSounds.play(vehicleBase, VehicleSounds.BALLISTA_LAUNCH, 6, 1);
    }
}
