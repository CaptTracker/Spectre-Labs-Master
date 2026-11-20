/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeCannonMobileFixed.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;

/** Type 11: mobile cannon, no turret. */
public class VehicleTypeCannonMobileFixed extends VehicleTypeCannon {
    public VehicleTypeCannonMobileFixed(int typeNum) {
        super(typeNum);
        this.configName = "cannon_mobile";
        this.displayName = nameKey();
        this.displayTooltip.add(tooltipKey("gunpowder"));
        this.displayTooltip.add(tooltipKey("mobile"));
        this.displayTooltip.add(tooltipKey("noturret"));
        this.width = 1.9f;
        this.height = 1.5f;
        this.baseForwardSpeed = 3.5f * 0.05f;
        this.baseStrafeSpeed = 1.5f;
        this.turretVerticalOffset = 14.5f * 0.0625f;
        this.riderSits = false;
        this.riderVerticalOffset = 0.35f;
        this.riderForwardsOffset = -2.45f;
        this.baseMissileVelocityMax = 38.f;
        this.validUpgrades.add(UpgradeRegistry.speedUpgrade);
    }
}
