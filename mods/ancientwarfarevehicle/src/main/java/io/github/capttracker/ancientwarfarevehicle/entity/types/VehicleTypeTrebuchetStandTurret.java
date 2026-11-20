/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeTrebuchetStandTurret.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import net.minecraft.resources.ResourceLocation;

/** Type 14: trebuchet on a stand with a 45 degree turret; it uses the mobile trebuchet's texture. */
public class VehicleTypeTrebuchetStandTurret extends VehicleTypeTrebuchet {
    public VehicleTypeTrebuchetStandTurret(int typeNum) {
        super(typeNum);
        this.configName = "trebuchet_stand_turret";
        this.displayName = nameKey();
        this.displayTooltip.add(tooltipKey("weight"));
        this.displayTooltip.add(tooltipKey("fixed"));
        this.displayTooltip.add(tooltipKey("midturret"));
        this.width = 2.7f;
        this.height = 2.7f;
        this.yawAdjustable = true;
        this.riderMovesWithTurret = true;
        this.riderSits = true;
        this.turretRotationMax = 45;
        this.riderForwardsOffset = 1.275f;
        this.riderVerticalOffset = 0.55f;
        this.turretVerticalOffset = (34.f + 67.5f + 24.0f + 9.5f) * 0.0625f;
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("trebuchet_mobile", level);
    }
}
