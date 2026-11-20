/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeCatapultStandFixed.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import net.minecraft.resources.ResourceLocation;

/** Type 0: catapult on a stand, fixed emplacement, no turret. */
public class VehicleTypeCatapultStandFixed extends VehicleTypeCatapult {
    public VehicleTypeCatapultStandFixed(int typeNum) {
        super(typeNum);
        configName = "catapult_stand";
        width = 2;
        height = 1.7f;
        baseMissileVelocityMax = 37.f;
        turretVerticalOffset = 3 * 0.0625f;
        riderForwardsOffset = 1.2f;
        riderVerticalOffset = 0.0f;
        displayName = nameKey();
        displayTooltip.add(tooltipKey("torsion"));
        displayTooltip.add(tooltipKey("fixed"));
        displayTooltip.add(tooltipKey("noturret"));
        storageBaySize = 0;
        armorBaySize = 4;
        upgradeBaySize = 4;
        yawAdjustable = false;
        drivable = true;
        baseForwardSpeed = 0.f;
        baseStrafeSpeed = .75f;
        riderSits = true;
        riderMovesWithTurret = false;
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("catapult_stand_fixed", level);
    }
}
