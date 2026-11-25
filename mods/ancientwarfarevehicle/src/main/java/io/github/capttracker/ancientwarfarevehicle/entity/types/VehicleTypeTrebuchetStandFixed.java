/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeTrebuchetStandFixed.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

/** Type 13: trebuchet on a stand, fixed emplacement (2 by 2, inherited). */
public class VehicleTypeTrebuchetStandFixed extends VehicleTypeTrebuchet {
    public VehicleTypeTrebuchetStandFixed(int typeNum) {
        super(typeNum);
        this.configName = "trebuchet_stand";
        this.displayName = nameKey();
        this.displayTooltip.add(tooltipKey("weight"));
        this.displayTooltip.add(tooltipKey("fixed"));
        this.displayTooltip.add(tooltipKey("noturret"));
        this.riderForwardsOffset = 1.425f;
        this.riderVerticalOffset = 0.35f;
        this.armorBaySize = 4;
        this.upgradeBaySize = 4;
    }
}
