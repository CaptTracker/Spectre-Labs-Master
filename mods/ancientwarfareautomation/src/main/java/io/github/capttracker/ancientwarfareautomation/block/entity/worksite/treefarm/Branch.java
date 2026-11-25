/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/treefarm/Branch.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A branch of a chorus plant: the child branches are cut first, the branch's own blocks only once it is mature
 * (it ends in a flower that has reached the last age, or in no flower at all).
 */
public class Branch implements ITree {
    private final List<Branch> childBranches = new ArrayList<>();
    private final List<BlockPos> trunkPositions = new ArrayList<>();
    private boolean mature;

    public void addChildBranch(Branch branch) {
        childBranches.add(branch);
    }

    public void addTrunkPos(BlockPos pos) {
        trunkPositions.add(0, pos);
    }

    public void setMature() {
        mature = true;
    }

    @Override
    public List<BlockPos> getTrunkPositions() {
        List<BlockPos> ret = childBranches.stream().map(Branch::getTrunkPositions).flatMap(Collection::stream).collect(Collectors.toCollection(ArrayList::new));
        if (mature) {
            ret.addAll(trunkPositions);
        }
        return ret;
    }

    private boolean isMature() {
        return mature;
    }

    @Override
    public List<BlockPos> getLeafPositions() {
        return Collections.emptyList();
    }

    public void updateMature() {
        if (childBranches.stream().allMatch(Branch::isMature)) {
            setMature();
        }
    }
}
