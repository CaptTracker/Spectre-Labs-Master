/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/BlockFinder.
 */
package io.github.capttracker.ancientwarfare.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.apache.commons.lang3.tuple.Pair;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects blocks of one kind (a block state matched by block and property values, where 1.12.2 matched block
 * and meta value) for the multiblock machines of Automation.
 */
public class BlockFinder {
    private final Level world;
    private final BlockState blockType;
    private final List<BlockPos> positions;

    public BlockFinder(Level worldIn, BlockState type, int size) {
        world = worldIn;
        blockType = type;
        positions = new ArrayList<>(size);
    }

    /**
     * Collect the block type connected to the start position, in a flood fill.
     *
     * @return the corners (minimum, maximum) of the box containing everything that was found
     */
    public Pair<BlockPos, BlockPos> cross(BlockPos initial) {
        List<BlockPos> allConnected = getAllConnectedBlocks(initial);

        int minX = initial.getX();
        int maxX = initial.getX();
        int minY = initial.getY();
        int maxY = initial.getY();
        int minZ = initial.getZ();
        int maxZ = initial.getZ();

        for (BlockPos pos : allConnected) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return Pair.of(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    /**
     * Collect blocks between corners in a box pattern. Fails fast if a block is not of the type.
     *
     * @param corners bottom north west corner and upper south east corner
     * @return true if all blocks between the corners are of the type
     */
    public boolean box(Pair<BlockPos, BlockPos> corners) {
        BlockPos temp;
        for (int x = corners.getLeft().getX(); x <= corners.getRight().getX(); x++) {
            for (int y = corners.getLeft().getY(); y <= corners.getRight().getY(); y++) {
                for (int z = corners.getLeft().getZ(); z <= corners.getRight().getZ(); z++) {
                    temp = new BlockPos(x, y, z);
                    if (!positions.contains(temp)) {
                        if (isTypeAt(temp)) {
                            positions.add(temp);
                        } else {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Collect all blocks of the type in the box that starts at the corner and has the given size.
     *
     * @param corner bottom north west corner
     * @param limit  the size of the box on each axis
     */
    public void connect(BlockPos corner, Vec3i limit) {
        BlockPos temp;
        for (int i = 0; i < limit.getX(); i++) {
            for (int j = 0; j < limit.getY(); j++) {
                for (int k = 0; k < limit.getZ(); k++) {
                    temp = corner.offset(i, j, k);
                    if (!positions.contains(temp) && isTypeAt(temp)) {
                        positions.add(temp);
                    }
                }
            }
        }
    }

    private boolean isTypeAt(BlockPos pos) {
        return world.getBlockState(pos) == blockType;
    }

    /** The collected block positions. */
    public List<BlockPos> getPositions() {
        return positions;
    }

    /** Flood fill over the six neighbours, up to 20 blocks away from the start (as the crow flies). */
    public List<BlockPos> getAllConnectedBlocks(BlockPos initialPos) {
        int maxRadius = 20;
        List<BlockPos> connectedBlocks = new ArrayList<>();
        Set<BlockPos> searchedPositions = new HashSet<>();

        connectedBlocks.add(initialPos);
        searchedPositions.add(initialPos);

        // Iterative instead of the original's recursion: same result, no stack overflow for large blobs.
        List<BlockPos> toVisit = new ArrayList<>();
        toVisit.add(initialPos);
        while (!toVisit.isEmpty()) {
            BlockPos currentPos = toVisit.remove(toVisit.size() - 1);
            if (Math.sqrt(currentPos.distSqr(initialPos)) >= maxRadius) {
                continue;
            }
            for (Direction facing : Direction.values()) {
                BlockPos offsetPos = currentPos.relative(facing);
                if (!searchedPositions.contains(offsetPos) && isTypeAt(offsetPos)) {
                    connectedBlocks.add(offsetPos);
                    searchedPositions.add(offsetPos);
                    toVisit.add(offsetPos);
                }
            }
        }

        return connectedBlocks;
    }
}
