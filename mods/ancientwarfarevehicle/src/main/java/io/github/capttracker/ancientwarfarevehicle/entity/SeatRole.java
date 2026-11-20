/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the roles of the seats of a vehicle.
 */
package io.github.capttracker.ancientwarfarevehicle.entity;

/** What a rider in a seat does. */
public enum SeatRole {
    /** Steers, and also aims and fires the vehicle's turret (as the one rider of every vehicle did before seats existed). */
    DRIVER,
    /** Aims and fires turret N (the seat's {@link VehicleSeat#turret()}); does not steer. */
    GUNNER,
    /** Just rides. */
    PASSENGER,
    /** Rows: adds speed to a water vehicle. */
    ROWER;

    /** True for the roles that work the vehicle's weapon: they may aim, fire and pick ammo. */
    public boolean operatesWeapon() {
        return this == DRIVER || this == GUNNER;
    }
}
