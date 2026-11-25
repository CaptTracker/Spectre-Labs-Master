/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeTrebuchetMobileFixed.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;

/** Type 15: mobile trebuchet, no turret (it uses the plain trebuchet texture, as in the original). */
public class VehicleTypeTrebuchetMobileFixed extends VehicleTypeTrebuchet {
    public VehicleTypeTrebuchetMobileFixed(int typeNum) {
        super(typeNum);
        this.configName = "trebuchet_mobile";
        this.displayName = nameKey();
        this.displayTooltip.add(tooltipKey("weight"));
        this.displayTooltip.add(tooltipKey("mobile"));
        this.displayTooltip.add(tooltipKey("noturret"));
        this.width = 2.7f;
        this.height = 2.7f;
        this.baseForwardSpeed = 3.7f * 0.05f;
        this.baseStrafeSpeed = 1.0f;
        this.riderSits = true;
        this.riderForwardsOffset = 1.275f;
        this.riderVerticalOffset = 0.65f;
        this.turretVerticalOffset = (34.f + 67.5f + 24.0f + 12.f) * 0.0625f;
        this.validUpgrades.add(UpgradeRegistry.speedUpgrade);
    }
}
