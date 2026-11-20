/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/vehicle/IVehicleUser.
 */
package io.github.capttracker.ancientwarfarenpc.entity.vehicle;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;

import java.util.Optional;

/**
 * An NPC that mans a vehicle ({@code NpcSiegeEngineer}, {@code NpcFactionSiegeEngineer}); the vehicle AIs are written against it.
 *
 * <p>Renamed from 1.12.2: {@code getVehicle} is {@link #getAssignedVehicle()}, {@code setVehicle} is {@link #setAssignedVehicle},
 * {@code resetVehicle} is {@link #resetAssignedVehicle()} and {@code getTarget} is {@link #getVehicleTarget()}, because the
 * entity methods {@code getVehicle()} (the entity it rides) and {@code getTarget()} (the attack target) of 1.21.1 have other return types.
 */
public interface IVehicleUser {
    void resetTarget();

    /** The vehicle this NPC is assigned to (found by the find vehicle AI), if it still exists. */
    Optional<VehicleBase> getAssignedVehicle();

    void setAssignedVehicle(VehicleBase vehicle);

    void resetAssignedVehicle();

    /** True if the assigned vehicle exists and the NPC rides it. */
    boolean isRidingVehicle();

    boolean canContinueRidingVehicle();

    /** The block or entity the vehicle should shoot at. */
    Optional<ITarget> getVehicleTarget();
}
