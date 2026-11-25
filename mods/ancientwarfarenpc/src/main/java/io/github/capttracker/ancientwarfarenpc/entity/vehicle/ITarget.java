/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/vehicle/ITarget.
 */
package io.github.capttracker.ancientwarfarenpc.entity.vehicle;

import io.github.capttracker.ancientwarfarevehicle.entity.IAimTarget;
import net.minecraft.world.level.Level;

/**
 * A target of a siege engineer: an entity or a block position. It is an {@link IAimTarget} so that the vehicle's aiming helper
 * can aim at it ({@code getBoundingBox} and {@code getX} and {@code getZ} come from there; the original spelled the box
 * {@code getBoundigBox}).
 */
public interface ITarget extends IAimTarget {
    double getY();

    boolean exists(Level world);
}
