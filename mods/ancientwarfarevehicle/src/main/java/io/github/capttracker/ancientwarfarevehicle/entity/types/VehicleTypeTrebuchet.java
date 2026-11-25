/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeTrebuchet.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSounds;
import io.github.capttracker.ancientwarfarevehicle.entity.materials.VehicleMaterial;
import io.github.capttracker.ancientwarfarevehicle.entity.varhelpers.TrebuchetVarHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;
import net.minecraft.resources.ResourceLocation;

/** Base of the three regular trebuchet types (power aimed, pitch fixed at 70 degrees). */
public abstract class VehicleTypeTrebuchet extends VehicleType {
    public VehicleTypeTrebuchet(int typeNum) {
        super(typeNum);
        configName = "trebuchet_base";
        vehicleMaterial = VehicleMaterial.materialWood;
        materialCount = 8;
        maxMissileWeight = 10.f;
        baseHealth = VehicleSettings.vehicleTrebuchetHealth;
        ShotAmmoLists.addStandardShot(validAmmoTypes);
        if (VehicleSettings.oversizeAmmoEnabled) {
            ShotAmmoLists.addOversizeShot(validAmmoTypes);
        }
        ammoBySoldierRank.put(0, AmmoRegistry.ammoStoneShot10);
        ammoBySoldierRank.put(1, AmmoRegistry.ammoStoneShot10);
        ammoBySoldierRank.put(2, AmmoRegistry.ammoStoneShot10);
        validArmors.add(ArmorRegistry.armorStone);
        validArmors.add(ArmorRegistry.armorIron);
        validArmors.add(ArmorRegistry.armorObsidian);
        validUpgrades.add(UpgradeRegistry.pitchDownUpgrade);
        validUpgrades.add(UpgradeRegistry.pitchUpUpgrade);
        validUpgrades.add(UpgradeRegistry.powerUpgrade);
        validUpgrades.add(UpgradeRegistry.reloadUpgrade);
        validUpgrades.add(UpgradeRegistry.aimUpgrade);
        pitchAdjustable = false;
        powerAdjustable = true;
        yawAdjustable = false;
        mountable = true;
        combatEngine = true;
        drivable = true;
        riderSits = false;
        riderMovesWithTurret = false;
        baseForwardSpeed = 0.f;
        baseStrafeSpeed = 0.5f;
        ammoBaySize = 6;
        armorBaySize = 3;
        upgradeBaySize = 3;
        storageBaySize = 0;
        accuracy = 0.85f;
        basePitchMax = 70.f;
        basePitchMin = 70.f;
        turretVerticalOffset = (34.f + 67.5f + 24.0f) * 0.0625f;
        baseMissileVelocityMax = 40.f;
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("trebuchet", level);
    }

    @Override
    public void playFiringSound(VehicleBase vehicleBase) {
        VehicleSounds.play(vehicleBase, VehicleSounds.TREBUCHET_LAUNCH, 6, 1);
    }

    @Override
    public void playReloadSound(VehicleBase vehicleBase) {
        VehicleSounds.play(vehicleBase, VehicleSounds.TREBUCHET_RELOAD, 2, 1);
    }

    @Override
    public VehicleFiringVarsHelper getFiringVarsHelper(VehicleBase veh) {
        return new TrebuchetVarHelper(veh);
    }
}
