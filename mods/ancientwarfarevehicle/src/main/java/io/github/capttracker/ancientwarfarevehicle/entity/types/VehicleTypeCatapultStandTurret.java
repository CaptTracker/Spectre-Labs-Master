/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeCatapultStandTurret.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import net.minecraft.resources.ResourceLocation;

/** Type 1: catapult on a stand with a 45 degree turret; the rider turns with the turret. */
public class VehicleTypeCatapultStandTurret extends VehicleTypeCatapult {
    public VehicleTypeCatapultStandTurret(int typeNum) {
        super(typeNum);
        configName = "catapult_stand_turret";
        width = 2.7f;
        height = 2.f;
        baseMissileVelocityMax = 32.f;
        // The original assigned 13 * 0.0625f first and then 0.4f; the last assignment wins.
        turretVerticalOffset = 0.4f;
        riderForwardsOffset = 1.2f;
        riderVerticalOffset = 0.55f;
        displayName = nameKey();
        displayTooltip.add(tooltipKey("torsion"));
        displayTooltip.add(tooltipKey("fixed"));
        displayTooltip.add(tooltipKey("midturret"));
        storageBaySize = 0;
        armorBaySize = 4;
        upgradeBaySize = 4;
        yawAdjustable = true;
        baseForwardSpeed = 0.f;
        baseStrafeSpeed = .75f;
        turretRotationMax = 45.f;
        drivable = true;
        riderSits = true;
        riderMovesWithTurret = true;
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("catapult_stand_turret", level);
    }
}
