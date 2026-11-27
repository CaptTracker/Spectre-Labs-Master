/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): the medium siege tower.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;

/** Type 26: the medium siege tower, a top platform 6 blocks over the ground (config {@code siege_tower_medium_height}), the driver and 2 pushers, 2 ladders, 4 platform seats, 2 horses. */
public class VehicleTypeSiegeTowerMedium extends VehicleTypeSiegeTower {
    public VehicleTypeSiegeTowerMedium(int typeNum) {
        super(typeNum, "siege_tower", VehicleSettings.siegeTowerMediumHeight, false);
    }
}
