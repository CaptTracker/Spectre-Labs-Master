/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): one seat of a vehicle type.
 */
package io.github.capttracker.ancientwarfarevehicle.entity;

/**
 * One seat. The offsets are the ones of the old single rider: {@code forward} along the negative yaw direction, {@code horizontal} along
 * yaw + 90 degrees and {@code vertical} up from the vehicle's position. {@code turret} is the number of the turret a {@link SeatRole#GUNNER}
 * works (every vehicle has the one turret 0 for now).
 */
public record VehicleSeat(SeatRole role, float forward, float horizontal, float vertical, int turret) {
    public VehicleSeat(SeatRole role, float forward, float horizontal, float vertical) {
        this(role, forward, horizontal, vertical, 0);
    }
}
