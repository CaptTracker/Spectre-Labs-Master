/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeCatapultMobileTurret.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;
import net.minecraft.resources.ResourceLocation;

/** Type 3: mobile catapult with a full (180 degrees either side) turret. */
public class VehicleTypeCatapultMobileTurret extends VehicleTypeCatapult {
    public VehicleTypeCatapultMobileTurret(int typeNum) {
        super(typeNum);
        this.configName = "catapult_mobile_turret";
        this.width = 2.7f;
        this.height = 2;
        this.baseStrafeSpeed = 1.5f;
        this.baseForwardSpeed = 4.0f * 0.05f;
        this.baseMissileVelocityMax = 30.f;
        this.turretVerticalOffset = 15 * 0.0625f;
        this.riderForwardsOffset = 0.8f;
        this.riderVerticalOffset = 0.65f;
        this.displayName = nameKey();
        this.displayTooltip.add(tooltipKey("torsion"));
        this.displayTooltip.add(tooltipKey("mobile"));
        this.displayTooltip.add(tooltipKey("fullturret"));
        this.storageBaySize = 0;
        this.armorBaySize = 3;
        this.upgradeBaySize = 3;
        this.yawAdjustable = true;
        this.drivable = true;
        this.riderSits = true;
        this.riderMovesWithTurret = true;
        this.turretRotationMax = 180.f;
        this.validUpgrades.add(UpgradeRegistry.speedUpgrade);
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("catapult_mobile_turret", level);
    }
}
