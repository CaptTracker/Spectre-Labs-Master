/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/vehicle/BlockPosTarget.
 */
package io.github.capttracker.ancientwarfarenpc.entity.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** A block as a target (the baton's attack area command); it exists while the block is not air. */
public class BlockPosTarget implements ITarget {
    private final BlockPos pos;
    private final AABB bounds;

    BlockPosTarget(BlockPos pos) {
        this.pos = pos;
        bounds = new AABB(pos);
    }

    @Override
    public double getX() {
        return pos.getX() + 0.5D;
    }

    @Override
    public double getY() {
        return pos.getY() + 0.5D;
    }

    @Override
    public double getZ() {
        return pos.getZ() + 0.5D;
    }

    @Override
    public AABB getBoundingBox() {
        return bounds;
    }

    @Override
    public boolean exists(Level world) {
        return !world.isEmptyBlock(pos);
    }

    public CompoundTag serializeToNBT(CompoundTag tag) {
        tag.putLong("targetPos", pos.asLong());
        return tag;
    }
}
