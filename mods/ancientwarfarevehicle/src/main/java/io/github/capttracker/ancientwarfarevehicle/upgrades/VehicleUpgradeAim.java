/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/upgrades/VehicleUpgradeAim.
 */
package io.github.capttracker.ancientwarfarevehicle.upgrades;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;

/** Halves the inaccuracy of the vehicle; stacks. */
public class VehicleUpgradeAim extends VehicleUpgradeBase {
    public VehicleUpgradeAim() {
        super("vehicle_upgrade_aim");
    }

    @Override
    public void applyVehicleEffects(VehicleBase vehicle) {
        float adj = 1 - vehicle.currentAccuracy;
        vehicle.currentAccuracy += adj * .5f;
        if (vehicle.currentAccuracy > 1) {
            vehicle.currentAccuracy = 1;
        }
    }
}
