/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (optional integration): the tree farm's scanner for the trees of Dynamic Trees.
 */
package io.github.capttracker.ancientwarfareautomation.compat.dynamictrees;

import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm.ITree;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm.ITreeScanner;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm.Tree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Finds a Dynamic Trees tree from any of its branch blocks: the trunk list is every branch block connected to it (not the roots), up to {@value #MAX_BRANCHES}.
 * It lists no leaves: the leaves of these trees come down with the tree and are never cut or sheared on their own. The farm fells the tree at its lowest branch
 * block, which is the foot of the trunk.
 */
final class DynamicTreeScanner implements ITreeScanner {
    private static final int MAX_BRANCHES = 2048;

    @Override
    public boolean matches(BlockState state) {
        return DynamicTreesSupport.isTrunkBranch(state);
    }

    @Override
    public boolean needsNaturalLeaves() {
        return false;
    }

    @Override
    public ITree scanTree(Level level, BlockPos pos, BlockPos minPos, BlockPos maxPos) {
        if (!DynamicTreesSupport.isTrunkBranch(level.getBlockState(pos))) {
            return Tree.EMPTY;
        }
        Tree tree = new Tree(pos.immutable());
        Set<BlockPos> seen = new HashSet<>();
        seen.add(pos.immutable());
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(pos.immutable());
        while (!open.isEmpty() && seen.size() < MAX_BRANCHES) {
            BlockPos current = open.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (seen.contains(next) || !BlockTools.isPositionWithinHorizontalBounds(next, minPos, maxPos) || !level.isLoaded(next)) {
                    continue;
                }
                if (DynamicTreesSupport.isTrunkBranch(level.getBlockState(next))) {
                    seen.add(next);
                    tree.addTrunkPosition(next);
                    open.add(next);
                }
            }
        }
        return tree;
    }
}
