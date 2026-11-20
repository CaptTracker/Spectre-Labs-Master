/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeHwacha.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.entity.materials.VehicleMaterial;
import io.github.capttracker.ancientwarfarevehicle.entity.varhelpers.HwachaVarHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;
import net.minecraft.resources.ResourceLocation;

/** Type 12: the hwacha rocket launcher: power aimed at a fixed 39 degrees, a salvo of 36 rockets. */
public class VehicleTypeHwacha extends VehicleType {
    public VehicleTypeHwacha(int typeNum) {
        super(typeNum);
        this.configName = "hwacha";
        this.vehicleMaterial = VehicleMaterial.materialWood;
        this.materialCount = 4;
        this.maxMissileWeight = 1.f;
        baseHealth = VehicleSettings.vehicleHwachaHealth;
        this.validAmmoTypes.add(AmmoRegistry.ammoRocket);
        this.validAmmoTypes.add(AmmoRegistry.ammoHwachaRocketFlame);
        this.validAmmoTypes.add(AmmoRegistry.ammoHwachaRocketExplosive);
        this.validAmmoTypes.add(AmmoRegistry.ammoHwachaRocketAirburst);
        this.ammoBySoldierRank.put(0, AmmoRegistry.ammoRocket);
        this.ammoBySoldierRank.put(1, AmmoRegistry.ammoRocket);
        this.ammoBySoldierRank.put(2, AmmoRegistry.ammoRocket);
        this.validArmors.add(ArmorRegistry.armorStone);
        this.validArmors.add(ArmorRegistry.armorIron);
        this.validArmors.add(ArmorRegistry.armorObsidian);
        this.validUpgrades.add(UpgradeRegistry.aimUpgrade);
        this.validUpgrades.add(UpgradeRegistry.pitchDownUpgrade);
        this.validUpgrades.add(UpgradeRegistry.pitchUpUpgrade);
        this.validUpgrades.add(UpgradeRegistry.powerUpgrade);
        this.validUpgrades.add(UpgradeRegistry.reloadUpgrade);
        this.validUpgrades.add(UpgradeRegistry.speedUpgrade);
        this.mountable = true;
        this.drivable = true;
        this.riderSits = false;
        this.riderMovesWithTurret = false;
        this.combatEngine = true;
        this.powerAdjustable = true;
        this.pitchAdjustable = false;
        this.yawAdjustable = false;
        this.accuracy = 0.75f;
        this.baseStrafeSpeed = 1.f;
        this.baseForwardSpeed = 3.5f * 0.05f;
        this.basePitchMax = 39;
        this.basePitchMin = 39;
        this.turretRotationMax = 0.f;
        // The original set 1.5 by 1.8 first and then overwrote it with 2 by 2.
        this.width = 2;
        this.height = 2;
        this.baseMissileVelocityMax = 42.f;
        this.turretVerticalOffset = 8 * 0.0625f;
        this.missileForwardsOffset = -0.9375f - 0.0625f;
        this.riderForwardsOffset = -1.4f;
        this.riderVerticalOffset = 0.35f;
        this.displayName = nameKey();
        this.displayTooltip.add(tooltipKey("gunpowder"));
        this.displayTooltip.add(tooltipKey("mobile"));
        this.displayTooltip.add(tooltipKey("noturret"));
        this.displayTooltip.add(tooltipKey("barrage"));
        this.storageBaySize = 0;
        this.armorBaySize = 3;
        this.ammoBaySize = 6;
        this.upgradeBaySize = 3;
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("hwacha", level);
    }

    @Override
    public VehicleFiringVarsHelper getFiringVarsHelper(VehicleBase veh) {
        return new HwachaVarHelper(veh);
    }
}
