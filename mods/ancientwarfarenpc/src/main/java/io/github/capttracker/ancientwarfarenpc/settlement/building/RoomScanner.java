/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (known-issue fixes): finds hand-built rooms (houses and chapels built by the player).
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Finds the enclosed room around a position by flood fill. A cell is open when nothing there blocks movement (air, torches, buttons, plants and so on);
 * everything with a collision shape is a wall of the room, so doors, glass panes, stairs, slabs and furniture all count as walls or obstacles and the fill
 * goes around them. A room is enclosed when the fill ends inside the size limit: an open roof or a gap in a wall lets the fill run out into the open and
 * fails, which also means that an enclosed room always has a roof. The room needs at least one door next to it.
 *
 * <p>Used for the hand-built family houses ({@code FamilyHomes}) and the hand-built chapels ({@link ChapelSites}). Server thread only; a cell in a chunk that
 * is not loaded makes the scan fail (the room is looked at again later).
 */
public final class RoomScanner {
    private static final Direction[] DIRECTIONS = Direction.values();

    private RoomScanner() {}

    /** An enclosed room: its open cells, the bounding box of the cells and how many doors touch it. */
    public record Room(Set<BlockPos> cells, BlockPos min, BlockPos max, int doors) {
        public boolean contains(BlockPos pos) {
            return cells.contains(pos);
        }

        /** Whether a block (a bed, a bell, a lectern) is part of the room: one of the cells next to it, or above it, is a cell of the room. */
        public boolean touches(BlockPos block) {
            for (Direction direction : DIRECTIONS) {
                if (cells.contains(block.relative(direction))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean isOpen(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * The open cell next to a block (above it first, then the sides, then below) that the fill can start from, or null if the block is walled in.
     */
    @Nullable
    public static BlockPos startNear(ServerLevel level, BlockPos block) {
        BlockPos above = block.above();
        if (level.isLoaded(above) && isOpen(level, above, level.getBlockState(above))) {
            return above;
        }
        for (Direction direction : DIRECTIONS) {
            BlockPos pos = block.relative(direction);
            if (level.isLoaded(pos) && isOpen(level, pos, level.getBlockState(pos))) {
                return pos;
            }
        }
        return null;
    }

    /**
     * The room that contains the open cell {@code start}, or null when the fill gets past {@code maxCells} cells (not enclosed), reaches a chunk that is not
     * loaded, starts in a wall, or the room has no door.
     */
    @Nullable
    public static Room scan(ServerLevel level, BlockPos start, int maxCells) {
        if (!level.isLoaded(start) || !isOpen(level, start, level.getBlockState(start))) {
            return null;
        }
        Set<BlockPos> cells = new HashSet<>();
        Set<BlockPos> doors = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos first = start.immutable();
        cells.add(first);
        queue.add(first);
        int minX = first.getX();
        int minY = first.getY();
        int minZ = first.getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction direction : DIRECTIONS) {
                BlockPos next = pos.relative(direction);
                if (cells.contains(next)) {
                    continue;
                }
                if (!level.isLoaded(next)) {
                    return null;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof DoorBlock) {
                    doors.add(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? next.immutable() : next.below());
                    continue;
                }
                if (!isOpen(level, next, state)) {
                    continue;
                }
                if (cells.size() >= maxCells) {
                    return null;
                }
                BlockPos fresh = next.immutable();
                cells.add(fresh);
                queue.add(fresh);
                minX = Math.min(minX, fresh.getX());
                minY = Math.min(minY, fresh.getY());
                minZ = Math.min(minZ, fresh.getZ());
                maxX = Math.max(maxX, fresh.getX());
                maxY = Math.max(maxY, fresh.getY());
                maxZ = Math.max(maxZ, fresh.getZ());
            }
        }
        if (doors.isEmpty()) {
            return null;
        }
        return new Room(cells, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), doors.size());
    }
}
