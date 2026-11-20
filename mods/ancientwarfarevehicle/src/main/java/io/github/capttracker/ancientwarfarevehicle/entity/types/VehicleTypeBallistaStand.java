/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeBallistaStand.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import net.minecraft.resources.ResourceLocation;

/** Type 4: ballista on a stand, fixed emplacement, no turret. */
public class VehicleTypeBallistaStand extends VehicleTypeBallista {
    public VehicleTypeBallistaStand(int typeNum) {
        super(typeNum);
        configName = "ballista_stand";
        baseMissileVelocityMax = 45.f;
        width = 1.2f;
        height = 1.4f;
        armorBaySize = 4;
        upgradeBaySize = 4;
        // 20 units vertical, 0 forwards
        turretVerticalOffset = 18.f * 0.0625f;
        riderForwardsOffset = -1.8f;
        riderVerticalOffset = 0.35f;
        riderSits = false;
        drivable = true;
        baseForwardSpeed = 0.f;
        baseStrafeSpeed = 1f;
        yawAdjustable = false;
        turretRotationMax = 0.f;
        displayName = nameKey();
        displayTooltip.add(tooltipKey("torsion"));
        displayTooltip.add(tooltipKey("fixed"));
        displayTooltip.add(tooltipKey("noturret"));
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("ballista_stand", level);
    }
}
