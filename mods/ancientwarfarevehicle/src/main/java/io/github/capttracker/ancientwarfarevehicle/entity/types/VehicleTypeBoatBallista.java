/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeBoatBallista.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleMovementType;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSounds;
import io.github.capttracker.ancientwarfarevehicle.entity.materials.VehicleMaterial;
import io.github.capttracker.ancientwarfarevehicle.entity.varhelpers.BallistaVarHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;
import net.minecraft.resources.ResourceLocation;

/** Type 18: a ballista on a boat, full turret, pitch aimed. */
public class VehicleTypeBoatBallista extends VehicleType {
    public VehicleTypeBoatBallista(int typeNum) {
        super(typeNum);
        configName = "boat_ballista";
        baseHealth = VehicleSettings.vehicleBallistaBoatHealth;
        vehicleMaterial = VehicleMaterial.materialWood;
        materialCount = 5;
        movementType = VehicleMovementType.WATER;
        maxMissileWeight = 2.f;
        ShotAmmoLists.addBallistaBolts(validAmmoTypes);
        ammoBySoldierRank.put(0, AmmoRegistry.ammoBallistaBolt);
        ammoBySoldierRank.put(1, AmmoRegistry.ammoBallistaBolt);
        ammoBySoldierRank.put(2, AmmoRegistry.ammoBallistaBoltFlame);
        validUpgrades.add(UpgradeRegistry.speedUpgrade);
        validUpgrades.add(UpgradeRegistry.pitchDownUpgrade);
        validUpgrades.add(UpgradeRegistry.pitchUpUpgrade);
        validUpgrades.add(UpgradeRegistry.pitchExtUpgrade);
        validUpgrades.add(UpgradeRegistry.powerUpgrade);
        validUpgrades.add(UpgradeRegistry.reloadUpgrade);
        validUpgrades.add(UpgradeRegistry.aimUpgrade);
        validArmors.add(ArmorRegistry.armorStone);
        validArmors.add(ArmorRegistry.armorObsidian);
        validArmors.add(ArmorRegistry.armorIron);
        armorBaySize = 3;
        upgradeBaySize = 3;
        ammoBaySize = 6;
        storageBaySize = 0;
        width = 2.7f;
        height = 1.4f;
        baseStrafeSpeed = 2.f;
        baseForwardSpeed = 6.2f * 0.05f;
        turretForwardsOffset = 23 * 0.0625f;
        turretVerticalOffset = 1.325f;
        accuracy = 0.98f;
        basePitchMax = 15;
        basePitchMin = -15;
        baseMissileVelocityMax = 42.f;
        riderForwardsOffset = -1.0f;
        riderVerticalOffset = 0.55f;
        addSeat(SeatRole.PASSENGER, 0.6f, 0.f, 0.55f);
        riderSits = true;
        mountable = true;
        drivable = true;
        combatEngine = true;
        pitchAdjustable = true;
        powerAdjustable = false;
        yawAdjustable = true;
        turretRotationMax = 360.f;
        displayName = nameKey();
        displayTooltip.add(tooltipKey("torsion"));
        displayTooltip.add(tooltipKey("boat"));
        displayTooltip.add(tooltipKey("fullturret"));
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("boat_ballista", level);
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
