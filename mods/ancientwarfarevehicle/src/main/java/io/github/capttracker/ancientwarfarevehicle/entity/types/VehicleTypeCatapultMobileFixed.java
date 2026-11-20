/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeCatapultMobileFixed.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;
import net.minecraft.resources.ResourceLocation;

/** Type 2: mobile catapult, no turret. */
public class VehicleTypeCatapultMobileFixed extends VehicleTypeCatapult {
    public VehicleTypeCatapultMobileFixed(int typeNum) {
        super(typeNum);
        this.configName = "catapult_mobile";
        this.width = 2.7f;
        this.height = 2;
        this.baseStrafeSpeed = 1.7f;
        this.baseForwardSpeed = 4.2f * 0.05f;
        this.baseMissileVelocityMax = 32.f;
        this.turretVerticalOffset = 0.9375f;
        this.riderForwardsOffset = 1.2f;
        this.riderVerticalOffset = 0.55f;
        this.displayName = nameKey();
        this.displayTooltip.add(tooltipKey("torsion"));
        this.displayTooltip.add(tooltipKey("mobile"));
        this.displayTooltip.add(tooltipKey("noturret"));
        this.storageBaySize = 0;
        this.armorBaySize = 3;
        this.upgradeBaySize = 3;
        this.yawAdjustable = false;
        this.drivable = true;
        this.riderSits = true;
        this.riderMovesWithTurret = false;
        this.validUpgrades.add(UpgradeRegistry.speedUpgrade);
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("catapult_mobile_fixed", level);
    }
}
