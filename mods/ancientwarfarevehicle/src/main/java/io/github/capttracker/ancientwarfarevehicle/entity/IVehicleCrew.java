/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: replaces the direct references of VehicleBase, VehicleAmmoHelper and VehicleFiringHelper to
 * NpcBase, NpcFaction, NpcSiegeEngineer and NpcFactionSiegeEngineer, which live in the NPC module.
 */
package io.github.capttracker.ancientwarfarevehicle.entity;

/**
 * Implemented by the NPC classes that can ride a vehicle as its crew (in the NPC module: {@code NpcBase} and
 * its subclasses). The Vehicles module cannot see the NPC module, so the vehicle asks the rider through this
 * interface.
 */
public interface IVehicleCrew {
    /** The rider is a faction soldier ({@code NpcFaction}): it never needs ammo and uses the type's rank ammo. */
    boolean isFactionSoldier();

    /** The rider is a player-owned siege engineer ({@code NpcSiegeEngineer}). */
    boolean isOwnedSiegeEngineer();

    /** The rider is a faction siege engineer ({@code NpcFactionSiegeEngineer}): the turret pitch range is widened by 12 degrees while it is aboard. */
    boolean isFactionSiegeEngineer();

    /** {@code getLevelingStats().getLevel()}: the rank used to pick the soldier ammo. */
    int getSoldierLevel();

    /** The seat role the crew member wants when it boards (the vehicle falls back to the next free seat that works the weapon). Siege engineers drive. */
    default SeatRole getPreferredSeatRole() {
        return SeatRole.DRIVER;
    }

    /** The vehicle's navigator detected that it is stuck ({@code NpcBase.onStuckDetected}). */
    void onVehicleStuck();
}
