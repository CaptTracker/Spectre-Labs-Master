/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeBallistaMobile.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;
import net.minecraft.resources.ResourceLocation;

/** Type 6: mobile ballista, no turret. */
public class VehicleTypeBallistaMobile extends VehicleTypeBallista {
    public VehicleTypeBallistaMobile(int typeNum) {
        super(typeNum);
        this.configName = "ballista_mobile";
        this.baseMissileVelocityMax = 42.f;
        this.width = 2.7f;
        this.height = 1.4f;
        this.armorBaySize = 3;
        this.upgradeBaySize = 3;
        this.baseStrafeSpeed = 1.7f;
        this.baseForwardSpeed = 4.2f * 0.05f;
        this.turretForwardsOffset = 1.f;
        this.turretVerticalOffset = 1.2f;
        this.riderForwardsOffset = -1.2f;
        this.riderVerticalOffset = 0.55f;
        this.riderSits = true;
        this.drivable = true;
        this.yawAdjustable = false;
        this.turretRotationMax = 0.f;
        this.displayName = nameKey();
        this.displayTooltip.add(tooltipKey("torsion"));
        this.displayTooltip.add(tooltipKey("mobile"));
        this.displayTooltip.add(tooltipKey("noturret"));
        this.validUpgrades.add(UpgradeRegistry.speedUpgrade);
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("ballista_mobile", level);
    }
}
