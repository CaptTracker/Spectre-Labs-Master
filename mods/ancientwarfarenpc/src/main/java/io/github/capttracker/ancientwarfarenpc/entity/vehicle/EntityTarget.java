/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/vehicle/EntityTarget.
 */
package io.github.capttracker.ancientwarfarenpc.entity.vehicle;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.lang.ref.WeakReference;

/** A living entity as a target; it is held weakly and exists while it has not been removed. */
public class EntityTarget implements ITarget {
    private final WeakReference<LivingEntity> entity;

    public EntityTarget(LivingEntity entity) {
        this.entity = new WeakReference<>(entity);
    }

    @Override
    public double getX() {
        LivingEntity e = entity.get();
        return e == null ? 0D : e.getX();
    }

    @Override
    public double getY() {
        LivingEntity e = entity.get();
        return e == null ? 0D : e.getY() + e.getEyeHeight();
    }

    @Override
    public double getZ() {
        LivingEntity e = entity.get();
        return e == null ? 0D : e.getZ();
    }

    @Override
    public AABB getBoundingBox() {
        LivingEntity e = entity.get();
        return e == null ? new AABB(0, 0, 0, 0, 0, 0) : e.getBoundingBox();
    }

    @Override
    public boolean exists(Level world) {
        LivingEntity e = entity.get();
        return e != null && !e.isRemoved();
    }
}
