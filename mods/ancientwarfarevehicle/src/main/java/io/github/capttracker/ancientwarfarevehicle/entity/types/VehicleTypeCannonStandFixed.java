/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeCannonStandFixed.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

/** Type 9: cannon on a stand, fixed emplacement. */
public class VehicleTypeCannonStandFixed extends VehicleTypeCannon {
    public VehicleTypeCannonStandFixed(int typeNum) {
        super(typeNum);
        configName = "cannon_stand";
        drivable = true;
        displayName = nameKey();
        displayTooltip.add(tooltipKey("gunpowder"));
        displayTooltip.add(tooltipKey("fixed"));
        displayTooltip.add(tooltipKey("noturret"));
        width = 1.2f;
        height = 1.4f;
        baseStrafeSpeed = 0.75f;
        baseForwardSpeed = 0.f;
        riderSits = false;
        riderVerticalOffset = 0.35f;
        riderForwardsOffset = -1.5f;
        armorBaySize = 4;
        upgradeBaySize = 4;
        ammoBaySize = 6;
    }
}
