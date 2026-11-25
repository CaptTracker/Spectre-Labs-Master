/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/treefarm/ITree.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm;

import net.minecraft.core.BlockPos;

import java.util.List;

/** The blocks of one tree the tree farm will cut. */
public interface ITree {
    List<BlockPos> getTrunkPositions();

    List<BlockPos> getLeafPositions();
}
