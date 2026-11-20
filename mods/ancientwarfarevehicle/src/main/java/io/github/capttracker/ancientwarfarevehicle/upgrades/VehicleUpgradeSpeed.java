/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/upgrades/VehicleUpgradeSpeed.
 */
package io.github.capttracker.ancientwarfarevehicle.upgrades;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;

/** Adds 0.25 blocks per second to the maximum forward speed; stacks. */
public class VehicleUpgradeSpeed extends VehicleUpgradeBase {
    public VehicleUpgradeSpeed() {
        super("vehicle_upgrade_speed");
    }

    @Override
    public void applyVehicleEffects(VehicleBase vehicle) {
        vehicle.currentForwardSpeedMax += 0.25f * 0.05f;
    }
}
