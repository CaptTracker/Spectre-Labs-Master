/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/registry/VehicleAmmoEntry (moved here because only the vehicle's
 * ammo helper and inventory use it).
 */
package io.github.capttracker.ancientwarfarevehicle.helpers;

import io.github.capttracker.ancientwarfarevehicle.missiles.IAmmo;

/** An ammo type and how many rounds of it a vehicle has. */
public class VehicleAmmoEntry {
    public final IAmmo baseAmmoType;
    public int ammoCount;

    public VehicleAmmoEntry(IAmmo ammo) {
        this.baseAmmoType = ammo;
    }
}
