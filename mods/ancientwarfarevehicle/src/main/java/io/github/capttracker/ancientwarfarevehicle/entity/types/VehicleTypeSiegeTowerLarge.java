/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): the large siege tower.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;

/** Type 27: the large siege tower, a top platform 9 blocks over the ground (config {@code siege_tower_large_height}), the driver and 4 pushers, 3 ladders, 6 platform seats, 2 horses. */
public class VehicleTypeSiegeTowerLarge extends VehicleTypeSiegeTower {
    public VehicleTypeSiegeTowerLarge(int typeNum) {
        super(typeNum, "siege_tower_large", VehicleSettings.siegeTowerLargeHeight, true);
    }
}
