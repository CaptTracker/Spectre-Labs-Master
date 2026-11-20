/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeBallistaStandTurret.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import net.minecraft.resources.ResourceLocation;

/** Type 5: ballista on a stand with a 45 degree turret; the rider turns with the turret. */
public class VehicleTypeBallistaStandTurret extends VehicleTypeBallista {
    public VehicleTypeBallistaStandTurret(int typeNum) {
        super(typeNum);
        configName = "ballista_stand_turret";
        baseMissileVelocityMax = 42.f;
        width = 1.2f;
        height = 1.4f;
        armorBaySize = 4;
        upgradeBaySize = 4;
        turretVerticalOffset = 18.f * 0.0625f;
        riderForwardsOffset = -1.8f;
        riderVerticalOffset = 0.35f;
        riderSits = false;
        drivable = true;
        baseForwardSpeed = 0.f;
        baseStrafeSpeed = 1f;
        riderMovesWithTurret = true;
        yawAdjustable = true;
        turretRotationMax = 45.f;
        displayName = nameKey();
        displayTooltip.add(tooltipKey("torsion"));
        displayTooltip.add(tooltipKey("fixed"));
        displayTooltip.add(tooltipKey("midturret"));
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("ballista_stand", level);
    }
}
