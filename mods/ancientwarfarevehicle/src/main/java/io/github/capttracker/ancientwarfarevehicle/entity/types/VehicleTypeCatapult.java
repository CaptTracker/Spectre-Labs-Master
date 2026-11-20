/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeCatapult.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSounds;
import io.github.capttracker.ancientwarfarevehicle.entity.materials.VehicleMaterial;
import io.github.capttracker.ancientwarfarevehicle.entity.varhelpers.CatapultVarHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;

/** Base of the four catapult types (power aimed, pitch fixed at 20 degrees). */
public abstract class VehicleTypeCatapult extends VehicleType {
    public VehicleTypeCatapult(int typeNum) {
        super(typeNum);
        configName = "catapult_base";
        vehicleMaterial = VehicleMaterial.materialWood;
        materialCount = 5;
        maxMissileWeight = 10.f;
        baseHealth = VehicleSettings.vehicleCatapultHealth;
        ShotAmmoLists.addStandardShot(validAmmoTypes);
        if (VehicleSettings.oversizeAmmoEnabled) {
            ShotAmmoLists.addOversizeShot(validAmmoTypes);
        }
        ammoBySoldierRank.put(0, AmmoRegistry.ammoStoneShot10);
        ammoBySoldierRank.put(1, AmmoRegistry.ammoStoneShot10);
        ammoBySoldierRank.put(2, AmmoRegistry.ammoStoneShot10);
        validArmors.add(ArmorRegistry.armorStone);
        validArmors.add(ArmorRegistry.armorObsidian);
        validArmors.add(ArmorRegistry.armorIron);
        mountable = true;
        combatEngine = true;
        powerAdjustable = true;
        pitchAdjustable = false;
        accuracy = 0.95f;
        baseStrafeSpeed = 2.f;
        baseForwardSpeed = 6.f * 0.05f;
        basePitchMax = 20;
        basePitchMin = 20;
        validUpgrades.add(UpgradeRegistry.aimUpgrade);
        validUpgrades.add(UpgradeRegistry.pitchDownUpgrade);
        validUpgrades.add(UpgradeRegistry.pitchUpUpgrade);
        validUpgrades.add(UpgradeRegistry.powerUpgrade);
        validUpgrades.add(UpgradeRegistry.reloadUpgrade);
        width = 2;
        height = 2;
        baseMissileVelocityMax = 32.f;
        missileVerticalOffset = 0;
        missileForwardsOffset = -2.0f;
        riderForwardsOffset = 1.2f;
        riderVerticalOffset = 0.55f;
        storageBaySize = 0;
        armorBaySize = 3;
        upgradeBaySize = 3;
        yawAdjustable = false;
        drivable = false;
        riderSits = true;
        riderMovesWithTurret = false;
    }

    @Override
    public void playFiringSound(VehicleBase vehicleBase) {
        VehicleSounds.play(vehicleBase, VehicleSounds.CATAPULT_LAUNCH, 6, 1);
    }

    @Override
    public void playReloadSound(VehicleBase vehicleBase) {
        VehicleSounds.play(vehicleBase, VehicleSounds.CATAPULT_RELOAD, 1, 1);
    }

    @Override
    public VehicleFiringVarsHelper getFiringVarsHelper(VehicleBase veh) {
        return new CatapultVarHelper(veh);
    }
}
