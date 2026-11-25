/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/upgrades/VehicleUpgradePower.
 */
package io.github.capttracker.ancientwarfarevehicle.upgrades;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;

/** Maximum launch speed x 1.1; stacks. */
public class VehicleUpgradePower extends VehicleUpgradeBase {
    public VehicleUpgradePower() {
        super("vehicle_upgrade_power");
    }

    @Override
    public void applyVehicleEffects(VehicleBase vehicle) {
        vehicle.currentLaunchSpeedPowerMax *= 1.1f;
    }
}
