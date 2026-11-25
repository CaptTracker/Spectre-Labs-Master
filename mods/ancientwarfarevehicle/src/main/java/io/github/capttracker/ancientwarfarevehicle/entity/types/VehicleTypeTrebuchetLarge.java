/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeTrebuchetLarge.
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

/** Type 16: the giant trebuchet (5 by 5, 30 kg ammo, 175 health by default). The 30 and 45 kg ammo is always valid here. */
public class VehicleTypeTrebuchetLarge extends VehicleType {
    public VehicleTypeTrebuchetLarge(int typeNum) {
        super(typeNum);
        configName = "trebuchet_giant";
        vehicleMaterial = VehicleMaterial.materialWood;
        materialCount = 20;
        maxMissileWeight = 30.f;
        baseHealth = VehicleSettings.vehicleGiantTrebuchetHealth;
        ShotAmmoLists.addStandardShot(validAmmoTypes);
        ShotAmmoLists.addOversizeShot(validAmmoTypes);

        ammoBySoldierRank.put(0, AmmoRegistry.ammoStoneShot30);
        ammoBySoldierRank.put(1, AmmoRegistry.ammoStoneShot30);
        ammoBySoldierRank.put(2, AmmoRegistry.ammoStoneShot30);

        validArmors.add(ArmorRegistry.armorStone);
        validArmors.add(ArmorRegistry.armorIron);
        validArmors.add(ArmorRegistry.armorObsidian);

        validUpgrades.add(UpgradeRegistry.pitchDownUpgrade);
        validUpgrades.add(UpgradeRegistry.pitchUpUpgrade);
        validUpgrades.add(UpgradeRegistry.powerUpgrade);
        validUpgrades.add(UpgradeRegistry.reloadUpgrade);
        validUpgrades.add(UpgradeRegistry.aimUpgrade);

        displayName = nameKey();
        displayTooltip.add(tooltipKey("weight"));
        displayTooltip.add(tooltipKey("fixed"));
        displayTooltip.add(tooltipKey("noturret"));
        displayTooltip.add(tooltipKey("large"));

        pitchAdjustable = false;
        powerAdjustable = true;
        yawAdjustable = false;
        mountable = true;
        combatEngine = true;
        drivable = true;
        riderSits = false;
        riderMovesWithTurret = false;

        width = 2 * 2.5f;
        height = 2 * 2.5f;
        riderForwardsOffset = 1.425f * 2.5f;
        riderVerticalOffset = 0.35f;
        baseMissileVelocityMax = 50.f;
        turretVerticalOffset = (34.f + 67.5f + 24.0f) * 0.0625f * 2.5f;

        baseForwardSpeed = 0.f;
        baseStrafeSpeed = 0.5f;
        ammoBaySize = 6;
        armorBaySize = 6;
        upgradeBaySize = 6;
        storageBaySize = 0;
        accuracy = 0.85f;

        basePitchMax = 70.f;
        basePitchMin = 70.f;
    }

    @Override
    public VehicleFiringVarsHelper getFiringVarsHelper(VehicleBase veh) {
        return new TrebuchetVarHelper(veh);
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("trebuchet", level);
    }

    @Override
    public void playFiringSound(VehicleBase vehicleBase) {
        VehicleSounds.play(vehicleBase, VehicleSounds.GIANT_TREBUCHET_LAUNCH, 6, 1);
    }

    @Override
    public void playReloadSound(VehicleBase vehicleBase) {
        VehicleSounds.play(vehicleBase, VehicleSounds.TREBUCHET_RELOAD, 1, 1);
    }
}
