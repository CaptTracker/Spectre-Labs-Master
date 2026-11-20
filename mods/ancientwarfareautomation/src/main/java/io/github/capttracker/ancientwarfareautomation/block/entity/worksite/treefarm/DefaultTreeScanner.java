/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/treefarm/DefaultTreeScanner.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm;

import io.github.capttracker.ancientwarfare.util.BlockTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The tree scanner for ordinary trees: a breadth first search from the starting trunk block over the neighbour
 * pattern of an {@link INextPositionGetter}, collecting trunk blocks (the trunk matcher) and leaf blocks (any of the
 * leaf matchers) inside the horizontal window. A leaf only counts if it is at most {@code maxLeafDistance} blocks
 * (horizontally) from the box that the trunk blocks found so far span; the box only grows with trunk blocks
 * within one block of it, so a branch that wanders off does not carry the leaf distance with it.
 *
 * <p>The fallback scanner of the {@code TreeFarmRegistry} uses the tags {@code minecraft:logs} and
 * {@code minecraft:leaves} (1.12.2: {@code Material.WOOD}, which also made planks, chests and crafting tables trunks).
 */
public class DefaultTreeScanner implements ITreeScanner {
    private static final int MAX_TRUNK_DISTANCE = 1;

    private final Predicate<BlockState> trunkMatcher;
    private final Set<Predicate<BlockState>> leafMatchers = new HashSet<>();
    private final int maxLeafDistance;
    private final INextPositionGetter nextPositionGetter;

    public Predicate<BlockState> getTrunkMatcher() {
        return trunkMatcher;
    }

    public void addLeafMatcher(Predicate<BlockState> leafMatcher) {
        leafMatchers.add(leafMatcher);
    }

    public DefaultTreeScanner(Predicate<BlockState> trunkMatcher, Predicate<BlockState> leafMatcher) {
        this(trunkMatcher, leafMatcher, CONNECTED_UP_OR_LEVEL, 5);
    }

    public DefaultTreeScanner(Predicate<BlockState> trunkMatcher, Predicate<BlockState> leafMatcher, INextPositionGetter nextPosGetter, int maxLeafDistance) {
        this.trunkMatcher = trunkMatcher;
        leafMatchers.add(leafMatcher);
        this.maxLeafDistance = maxLeafDistance;
        nextPositionGetter = nextPosGetter;
    }

    @Override
    public ITree scanTree(Level level, BlockPos pos, BlockPos minPos, BlockPos maxPos) {
        HorizontalAABB trunkBounds = new HorizontalAABB(pos);
        if (!isTrunk(level.getBlockState(pos))) {
            return Tree.EMPTY;
        }
        List<BlockPos> openList = new ArrayList<>();
        Set<BlockPos> alreadyScanned = new HashSet<>();
        openList.add(pos);
        alreadyScanned.add(pos);

        Tree tree = new Tree(pos);
        while (!openList.isEmpty()) {
            BlockPos current = openList.remove(0);
            Set<BlockPos> toScan = nextPositionGetter.getPositionsToScan(current).filter(p -> !alreadyScanned.contains(p))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            openList.addAll(addTreeBlocks(toScan, level, tree, trunkBounds, minPos, maxPos));
            alreadyScanned.addAll(toScan);
        }
        return tree;
    }

    private Collection<? extends BlockPos> addTreeBlocks(Set<BlockPos> toScan, Level level, Tree tree, HorizontalAABB trunkBounds, BlockPos minPos, BlockPos maxPos) {
        Set<BlockPos> treeBlocks = new HashSet<>();
        for (BlockPos pos : toScan) {
            if (!BlockTools.isPositionWithinHorizontalBounds(pos, minPos, maxPos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (isTrunk(state)) {
                tree.addTrunkPosition(pos);
                treeBlocks.add(pos);
                if (trunkBounds.distanceTo(pos) <= MAX_TRUNK_DISTANCE) {
                    trunkBounds.include(pos);
                }
            } else if (isLeaf(state) && trunkBounds.distanceTo(pos) <= maxLeafDistance) {
                tree.addLeafPosition(pos);
                treeBlocks.add(pos);
            }
        }
        return treeBlocks;
    }

    private boolean isLeaf(BlockState state) {
        return leafMatchers.stream().anyMatch(m -> m.test(state));
    }

    private boolean isTrunk(BlockState state) {
        return trunkMatcher.test(state);
    }

    /** The positions of the box, the lowest layer first; inside a layer x runs fastest, then z. */
    private static List<BlockPos> getPositionsInBoxOrderedByY(BlockPos corner1, BlockPos corner2) {
        int x1 = Math.min(corner1.getX(), corner2.getX());
        int y1 = Math.min(corner1.getY(), corner2.getY());
        int z1 = Math.min(corner1.getZ(), corner2.getZ());
        int x2 = Math.max(corner1.getX(), corner2.getX());
        int y2 = Math.max(corner1.getY(), corner2.getY());
        int z2 = Math.max(corner1.getZ(), corner2.getZ());
        List<BlockPos> ret = new ArrayList<>((x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1));
        for (int y = y1; y <= y2; y++) {
            for (int z = z1; z <= z2; z++) {
                for (int x = x1; x <= x2; x++) {
                    ret.add(new BlockPos(x, y, z));
                }
            }
        }
        return ret;
    }

    @Override
    public boolean matches(BlockState state) {
        return trunkMatcher.test(state);
    }

    /** Which positions are looked at next from a block of the tree. */
    public interface INextPositionGetter {
        Stream<BlockPos> getPositionsToScan(BlockPos currentPos);
    }

    /** The 26 neighbours. */
    public static final INextPositionGetter ALL_AROUND = currentPos -> getPositionsInBoxOrderedByY(currentPos.offset(-1, -1, -1), currentPos.offset(1, 1, 1)).stream();

    /** The 6 face neighbours. */
    public static final INextPositionGetter CONNECTED_AROUND = currentPos -> Arrays.stream(Direction.values()).map(currentPos::relative);

    /** The four horizontal neighbours and the one above. */
    public static final INextPositionGetter CONNECTED_UP_OR_LEVEL = new INextPositionGetter() {
        private final Direction[] offsets = new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP};

        @Override
        public Stream<BlockPos> getPositionsToScan(BlockPos currentPos) {
            return Arrays.stream(offsets).map(currentPos::relative);
        }
    };

    /** The four horizontal neighbours and the one below. */
    public static final INextPositionGetter CONNECTED_DOWN_OR_LEVEL = new INextPositionGetter() {
        private final Direction[] offsets = new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN};

        @Override
        public Stream<BlockPos> getPositionsToScan(BlockPos currentPos) {
            return Arrays.stream(offsets).map(currentPos::relative);
        }
    };

    /** The neighbours of the same layer and of the layer above (17). */
    public static final INextPositionGetter ALL_UP_OR_LEVEL = currentPos -> getPositionsInBoxOrderedByY(currentPos.offset(-1, 0, -1), currentPos.offset(1, 1, 1)).stream();
}
