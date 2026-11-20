/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/IPathableEntity.
 */
package io.github.capttracker.ancientwarfarevehicle.entity;

import io.github.capttracker.ancientwarfarevehicle.pathing.Node;
import net.minecraft.world.entity.Entity;

import java.util.List;

/** An entity driven by the custom {@link io.github.capttracker.ancientwarfarevehicle.pathing.Navigator} (dormant, see the port notes). */
public interface IPathableEntity {
    void setPath(List<Node> path);

    void setMoveTo(double x, double y, double z, float moveSpeed);

    float getDefaultMoveSpeed();

    boolean isPathableEntityOnLadder();

    Entity getEntity();

    void onStuckDetected();
}
