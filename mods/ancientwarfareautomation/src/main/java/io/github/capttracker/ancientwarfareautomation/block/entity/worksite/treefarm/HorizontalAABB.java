/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/treefarm/HorizontalAABB.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm;

import net.minecraft.core.BlockPos;

/** A growing box on the horizontal plane (the trunk of the tree being scanned), to measure how far a leaf is from the trunk. */
public class HorizontalAABB {
    private int minX;
    private int minZ;
    private int maxX;
    private int maxZ;

    public HorizontalAABB(BlockPos pos) {
        minX = pos.getX();
        maxX = pos.getX();
        minZ = pos.getZ();
        maxZ = pos.getZ();
    }

    public void include(BlockPos pos) {
        this.minX = Math.min(this.minX, pos.getX());
        this.minZ = Math.min(this.minZ, pos.getZ());
        this.maxX = Math.max(this.maxX, pos.getX());
        this.maxZ = Math.max(this.maxZ, pos.getZ());
    }

    public int distanceTo(BlockPos pos) {
        int xDistance = pos.getX() > minX && pos.getX() < maxX ? 0 : Math.min(Math.abs(pos.getX() - minX), Math.abs(pos.getX() - maxX));
        int zDistance = pos.getZ() > minZ && pos.getZ() < maxZ ? 0 : Math.min(Math.abs(pos.getZ() - minZ), Math.abs(pos.getZ() - maxZ));
        return Math.max(xDistance, zDistance);
    }
}
