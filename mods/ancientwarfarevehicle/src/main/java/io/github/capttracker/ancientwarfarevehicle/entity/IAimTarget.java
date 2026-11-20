/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: the part of npc/entity/vehicle/ITarget that the aiming helper needs.
 */
package io.github.capttracker.ancientwarfarevehicle.entity;

import net.minecraft.world.phys.AABB;

/** A target a vehicle can be aimed at. The NPC module's {@code ITarget} extends this. */
public interface IAimTarget {
    /** The bounds of the target (spelled {@code getBoundigBox} in the original). */
    AABB getBoundingBox();

    double getX();

    double getZ();
}
