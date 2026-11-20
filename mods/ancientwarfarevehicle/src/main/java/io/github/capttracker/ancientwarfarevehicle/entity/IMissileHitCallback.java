/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/IMissileHitCallback.
 */
package io.github.capttracker.ancientwarfarevehicle.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Used by missiles to report where they hit to the vehicle and soldier that fired them (statistics only,
 * effectively server side).
 */
public interface IMissileHitCallback {
    /** A fired missile impacted a position. */
    void onMissileImpact(Level level, double x, double y, double z);

    /** A fired missile impacted an entity. */
    void onMissileImpactEntity(Level level, Entity entity);
}
