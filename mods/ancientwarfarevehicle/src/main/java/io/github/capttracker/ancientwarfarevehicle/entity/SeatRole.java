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
    ROWER,
    /** Rework phase 12: pushes a siege tower or a mantlet from behind. Each pusher adds to the speed; the driver pushes too. */
    PUSHER,
    /** Rework phase 12: on the ladder column inside a siege tower; the rider climbs up and down with the movement keys. */
    LADDER,
    /** Rework phase 12: on the top platform of a siege tower; may work the drop bridge and step out onto it. */
    PLATFORM;

    /** True for the roles that work the vehicle's weapon: they may aim, fire and pick ammo. */
    public boolean operatesWeapon() {
        return this == DRIVER || this == GUNNER;
    }

    /** True for the roles of the siege works (rework phase 12): NPC crew may take them besides the weapon seats. */
    public boolean isSiegeRole() {
        return this == PUSHER || this == LADDER || this == PLATFORM;
    }

    /** True for the roles that add their push to a siege tower or a mantlet: the driver pushes too. */
    public boolean pushes() {
        return this == DRIVER || this == PUSHER;
    }
}
