/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/pathing/PathWorldAccessEntity.
 */
package io.github.capttracker.ancientwarfarevehicle.pathing;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * A wrapper for a level that was meant to do additional validation of nodes to see if the entity can walk on
 * the node and fit in the area (the code for that is commented out in the original, which has a "TODO look into
 * completely removing this class").
 *
 * <p>Fixes the original constructor, which assigned the {@code world} parameter to itself and left the field
 * null, so that {@code isRemote()} would have thrown.
 */
public class PathWorldAccessEntity extends PathWorldAccess {
    protected final Entity entity;

    public PathWorldAccessEntity(Level level, Entity entity) {
        super(level);
        this.entity = entity;
    }
}
