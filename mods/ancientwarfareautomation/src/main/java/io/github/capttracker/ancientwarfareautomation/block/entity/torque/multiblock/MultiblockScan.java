/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/BlockFinder as the flywheel storage and the windmill blades used it
 * (cross, box, connect, getAllConnectedBlocks), rewritten on position sets.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.torque.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Flood fill and box checks for the flywheel stack and the windmill plane.
 *
 * <p>Differences from the 1.12.2 {@code BlockFinder}: the blocks are matched by a predicate on the block state (the
 * metadata of the tier is a block of its own now), the fill is iterative with hash sets (the original recursed a few
 * thousand levels deep and used {@code List.contains}), and the box test no longer looks up blocks it already knows.
 * The behaviour is the same: the fill goes over the six neighbours and does not expand from a position that is 20 or
 * more blocks (straight line) from the start.
 */
public final class MultiblockScan {
    /** Blocks further from the start than this are not expanded (BlockFinder.maxRadius). */
    public static final int MAX_RADIUS = 20;

    private MultiblockScan() {
    }

    /** The result of a fill: the connected positions and the corners of the box around them (the start included). */
    public record Region(Set<BlockPos> positions, BlockPos min, BlockPos max) {
        public int sizeX() {
            return max.getX() - min.getX() + 1;
        }

        public int sizeY() {
            return max.getY() - min.getY() + 1;
        }

        public int sizeZ() {
            return max.getZ() - min.getZ() + 1;
        }
    }

    /** All blocks that match, connected to the start over faces, within the radius. The start is always part of the result. */
    public static Set<BlockPos> floodFill(Level level, BlockPos start, Predicate<BlockState> matches) {
        Set<BlockPos> found = new LinkedHashSet<>();
        found.add(start);
        Deque<BlockPos> toVisit = new ArrayDeque<>();
        toVisit.add(start);
        while (!toVisit.isEmpty()) {
            BlockPos current = toVisit.removeLast();
            if (Math.sqrt(current.distSqr(start)) >= MAX_RADIUS) {
                continue;
            }
            for (Direction facing : Direction.values()) {
                BlockPos offset = current.relative(facing);
                if (!found.contains(offset) && isMatch(level, offset, matches)) {
                    found.add(offset);
                    toVisit.add(offset);
                }
            }
        }
        return found;
    }

    /** {@code BlockFinder.cross}: the fill and the corners of its bounding box. */
    public static Region cross(Level level, BlockPos start, Predicate<BlockState> matches) {
        Set<BlockPos> connected = floodFill(level, start, matches);
        int minX = start.getX();
        int maxX = start.getX();
        int minY = start.getY();
        int maxY = start.getY();
        int minZ = start.getZ();
        int maxZ = start.getZ();
        for (BlockPos pos : connected) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new Region(connected, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    /**
     * {@code BlockFinder.box}: true if every position of the box between the corners (inclusive) is a matching block.
     * The positions of the box are those that were looked up in the world, so a block of the type that the fill did not
     * reach counts as well, as in the original.
     */
    public static boolean isBoxFilled(Level level, Region region, Predicate<BlockState> matches) {
        for (int x = region.min().getX(); x <= region.max().getX(); x++) {
            for (int y = region.min().getY(); y <= region.max().getY(); y++) {
                for (int z = region.min().getZ(); z <= region.max().getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!region.positions().contains(pos) && !isMatch(level, pos, matches)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Every position of the box between the corners (inclusive), in x, y, z order. */
    public static Set<BlockPos> boxPositions(Region region) {
        Set<BlockPos> all = new LinkedHashSet<>();
        for (int x = region.min().getX(); x <= region.max().getX(); x++) {
            for (int y = region.min().getY(); y <= region.max().getY(); y++) {
                for (int z = region.min().getZ(); z <= region.max().getZ(); z++) {
                    all.add(new BlockPos(x, y, z));
                }
            }
        }
        return Collections.unmodifiableSet(all);
    }

    private static boolean isMatch(Level level, BlockPos pos, Predicate<BlockState> matches) {
        return level.isLoaded(pos) && matches.test(level.getBlockState(pos));
    }
}
