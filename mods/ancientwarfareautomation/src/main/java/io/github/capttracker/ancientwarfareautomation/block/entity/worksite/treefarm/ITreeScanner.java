/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/treefarm/ITreeScanner.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Finds the blocks of a tree, starting from one of its trunk blocks. The tree farm picks the scanner by the trunk block. */
public interface ITreeScanner {
    /**
     * @param pos    the block the scan starts at
     * @param minPos lower corner of the area (horizontal) the tree may extend into
     * @param maxPos upper corner of that area
     */
    ITree scanTree(Level level, BlockPos pos, BlockPos minPos, BlockPos maxPos);

    boolean matches(BlockState state);

    /**
     * True if the staffed workers only cut the trunks of this scanner's trees that have natural leaves (logs without them are a player's build). A scanner of
     * trees that are no blocks of logs and leaves (Dynamic Trees) says false.
     */
    default boolean needsNaturalLeaves() {
        return true;
    }
}
