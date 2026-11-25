/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeBallistaMobileTurret.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * Type 7: mobile ballista with a full turret. Does not set {@code riderMovesWithTurret} (as in the original), and
 * inherits the base forward speed (4.0) and strafe speed (2.25).
 */
public class VehicleTypeBallistaMobileTurret extends VehicleTypeBallista {
    public VehicleTypeBallistaMobileTurret(int typeNum) {
        super(typeNum);
        this.configName = "ballista_mobile_turret";
        this.baseMissileVelocityMax = 37.f;
        this.width = 2.7f;
        this.height = 1.4f;
        this.armorBaySize = 3;
        this.upgradeBaySize = 3;
        this.ammoBaySize = 6;
        this.turretForwardsOffset = 1.f;
        this.turretVerticalOffset = 1.2f;
        this.riderForwardsOffset = -1.2f;
        this.riderVerticalOffset = 0.55f;
        this.riderSits = true;
        this.drivable = true;
        this.yawAdjustable = true;
        this.turretRotationMax = 360.f;
        this.displayName = nameKey();
        this.displayTooltip.add(tooltipKey("torsion"));
        this.displayTooltip.add(tooltipKey("mobile"));
        this.displayTooltip.add(tooltipKey("fullturret"));
        this.validUpgrades.add(UpgradeRegistry.speedUpgrade);
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("ballista_mobile", level);
    }
}
