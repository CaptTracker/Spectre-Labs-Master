/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/Zone.
 */
package io.github.capttracker.ancientwarfare.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** A box of block positions, minimum and maximum corner both inclusive. */
public class Zone {
    public BlockPos min;
    public BlockPos max;

    public Zone(BlockPos p1, BlockPos p2) {
        min = BlockTools.getMin(p1, p2);
        max = BlockTools.getMax(p1, p2);
    }

    public Zone() {
        min = BlockPos.ZERO;
        max = BlockPos.ZERO;
    }

    private boolean intersects(BlockPos min, BlockPos max) {
        return intersects(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
    }

    public boolean intersects(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return max.getX() >= minX && max.getY() >= minY && max.getZ() >= minZ && min.getX() <= maxX && min.getY() <= maxY && min.getZ() <= maxZ;
    }

    /** Does the input share any block position with this zone? */
    public boolean intersects(Zone z) {
        return intersects(z.min, z.max);
    }

    public boolean equals(BlockPos min, BlockPos max) {
        return min.equals(this.min) && max.equals(this.max);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof Zone zone && this.equals(zone.min, zone.max);
    }

    @Override
    public int hashCode() {
        return 31 * min.hashCode() + max.hashCode();
    }

    @Override
    public String toString() {
        return String.format("From %s to %s", min, max);
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("min", min.asLong());
        tag.putLong("max", max.asLong());
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        min = BlockPos.of(tag.getLong("min"));
        max = BlockPos.of(tag.getLong("max"));
    }
}
