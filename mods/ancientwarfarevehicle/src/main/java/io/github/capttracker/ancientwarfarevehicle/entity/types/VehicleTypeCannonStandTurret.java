/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeCannonStandTurret.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

/** Type 10: cannon on a stand with a 45 degree turret; the rider turns with the turret. */
public class VehicleTypeCannonStandTurret extends VehicleTypeCannon {
    public VehicleTypeCannonStandTurret(int typeNum) {
        super(typeNum);
        configName = "cannon_stand_turret";
        displayName = nameKey();
        displayTooltip.add(tooltipKey("gunpowder"));
        displayTooltip.add(tooltipKey("fixed"));
        displayTooltip.add(tooltipKey("midturret"));
        width = 1.2f;
        height = 1.6f;
        turretRotationMax = 45;
        riderMovesWithTurret = true;
        yawAdjustable = true;
        riderSits = true;
        baseStrafeSpeed = 0.75f;
        baseForwardSpeed = 0.f;
        baseMissileVelocityMax = 40.f;
        turretVerticalOffset = 13.5f * 0.0625f;
        riderVerticalOffset = 0.55f;
        riderForwardsOffset = -1.25f;
    }
}
