/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/upgrades/VehicleUpgradePitchUp.
 */
package io.github.capttracker.ancientwarfarevehicle.upgrades;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;

/** Moves the pitch range up by 3 degrees and clamps the current pitch into it. */
public class VehicleUpgradePitchUp extends VehicleUpgradeBase {
    public VehicleUpgradePitchUp() {
        super("vehicle_upgrade_pitch_up");
    }

    @Override
    public void applyVehicleEffects(VehicleBase vehicle) {
        vehicle.currentTurretPitchMax += 3;
        vehicle.currentTurretPitchMin += 3;
        if (vehicle.localTurretPitch < vehicle.currentTurretPitchMin) {
            vehicle.localTurretPitch = vehicle.currentTurretPitchMin;
        }
        if (vehicle.localTurretPitch > vehicle.currentTurretPitchMax) {
            vehicle.localTurretPitch = vehicle.currentTurretPitchMax;
        }
    }
}
