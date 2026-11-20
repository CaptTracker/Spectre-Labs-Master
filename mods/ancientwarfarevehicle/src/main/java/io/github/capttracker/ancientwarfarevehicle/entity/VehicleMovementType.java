/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/VehicleMovementType.
 */
package io.github.capttracker.ancientwarfarevehicle.entity;

/** How a vehicle type moves. Only {@link #GROUND} and {@link #WATER} are used by any type; the rest is kept for the original's air code. */
public enum VehicleMovementType {
    NONE,
    GROUND,
    WATER,
    /** airplane style */
    AIR1,
    /** helicopter style */
    AIR2,
    WATER2
}
