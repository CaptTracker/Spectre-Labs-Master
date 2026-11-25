/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/upgrades/VehicleUpgradeTurretPitch.
 */
package io.github.capttracker.ancientwarfarevehicle.upgrades;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;

/** Widens the pitch range by 3 degrees up and down, on vehicles that aim by pitch. */
public class VehicleUpgradeTurretPitch extends VehicleUpgradeBase {
    public VehicleUpgradeTurretPitch() {
        super("vehicle_upgrade_turret_pitch");
    }

    @Override
    public void applyVehicleEffects(VehicleBase vehicle) {
        if (vehicle.canAimPitch()) {
            vehicle.currentTurretPitchMax += 3;
            vehicle.currentTurretPitchMin -= 3;
        }
    }
}
