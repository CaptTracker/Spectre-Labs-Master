/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeBoatCatapult.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleMovementType;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSounds;
import io.github.capttracker.ancientwarfarevehicle.entity.materials.VehicleMaterial;
import io.github.capttracker.ancientwarfarevehicle.entity.varhelpers.CatapultVarHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;
import net.minecraft.resources.ResourceLocation;

/** Type 19: a catapult on a boat, no turret, power aimed. */
public class VehicleTypeBoatCatapult extends VehicleType {
    public VehicleTypeBoatCatapult(int typeNum) {
        super(typeNum);
        configName = "boat_catapult";
        baseHealth = VehicleSettings.vehicleCatapultBoatHealth;
        vehicleMaterial = VehicleMaterial.materialWood;
        materialCount = 5;
        movementType = VehicleMovementType.WATER;
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
        validUpgrades.add(UpgradeRegistry.aimUpgrade);
        validUpgrades.add(UpgradeRegistry.pitchDownUpgrade);
        validUpgrades.add(UpgradeRegistry.pitchUpUpgrade);
        validUpgrades.add(UpgradeRegistry.powerUpgrade);
        validUpgrades.add(UpgradeRegistry.speedUpgrade);
        validUpgrades.add(UpgradeRegistry.reloadUpgrade);
        storageBaySize = 0;
        armorBaySize = 3;
        upgradeBaySize = 3;
        drivable = true;
        riderSits = true;
        riderMovesWithTurret = false;
        mountable = true;
        combatEngine = true;
        powerAdjustable = true;
        pitchAdjustable = false;
        yawAdjustable = false;
        width = 2.7f;
        height = 1.4f;
        baseStrafeSpeed = 2.f;
        baseForwardSpeed = 6.2f * 0.05f;
        accuracy = 0.95f;
        basePitchMax = 20;
        basePitchMin = 20;
        baseMissileVelocityMax = 32.f;
        maxMissileWeight = 10.f;
        missileVerticalOffset = 0;
        missileForwardsOffset = -37 * 0.0625f;
        turretVerticalOffset = 18 * 0.0625f;
        riderForwardsOffset = 1.10f;
        riderVerticalOffset = 0.55f;
        addSeat(SeatRole.PASSENGER, -0.9f, 0.f, 0.55f);
        displayName = nameKey();
        displayTooltip.add(tooltipKey("torsion"));
        displayTooltip.add(tooltipKey("boat"));
        displayTooltip.add(tooltipKey("noturret"));
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("boat_catapult", level);
    }

    @Override
    public VehicleFiringVarsHelper getFiringVarsHelper(VehicleBase veh) {
        return new CatapultVarHelper(veh);
    }

    @Override
    public void playFiringSound(VehicleBase vehicleBase) {
        VehicleSounds.play(vehicleBase, VehicleSounds.CATAPULT_LAUNCH, 6, 1);
    }

    @Override
    public void playReloadSound(VehicleBase vehicleBase) {
        VehicleSounds.play(vehicleBase, VehicleSounds.CATAPULT_RELOAD, 1, 1);
    }
}
