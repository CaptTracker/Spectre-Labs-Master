/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/upgrades/VehicleUpgradeReload.
 */
package io.github.capttracker.ancientwarfarevehicle.upgrades;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;

/** Reload time x 0.9 (truncated to whole ticks each time, as in 1.12.2); stacks. */
public class VehicleUpgradeReload extends VehicleUpgradeBase {
    public VehicleUpgradeReload() {
        super("vehicle_upgrade_reload");
    }

    @Override
    public void applyVehicleEffects(VehicleBase vehicle) {
        vehicle.currentReloadTicks *= 0.9f;
    }
}
