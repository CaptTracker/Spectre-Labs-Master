/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/treefarm/Tree.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** The result of the default tree scan: trunk and leaf positions, the newest (highest, outermost) first. */
public class Tree implements ITree {
    /** The result for a position that is not part of a tree. Do not add to it. */
    public static final Tree EMPTY = new Tree();

    private final List<BlockPos> trunkPositions = new ArrayList<>();
    private final List<BlockPos> leafPositions = new ArrayList<>();

    private Tree() {
    }

    public Tree(BlockPos initialTrunkPos) {
        trunkPositions.add(initialTrunkPos);
    }

    public void addTrunkPosition(BlockPos trunkPosition) {
        trunkPositions.add(0, trunkPosition);
    }

    public void addLeafPosition(BlockPos leafPosition) {
        leafPositions.add(0, leafPosition);
    }

    @Override
    public List<BlockPos> getTrunkPositions() {
        return trunkPositions;
    }

    @Override
    public List<BlockPos> getLeafPositions() {
        return leafPositions;
    }
}
