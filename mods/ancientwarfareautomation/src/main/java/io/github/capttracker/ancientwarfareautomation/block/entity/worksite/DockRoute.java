/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the water routes of the fishing dock's boat.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Finds the waypoints a ship steers through over water: a breadth first search over the cells (1 x 1 blocks) of the water, round the dock, then the
 * path is cut short wherever a straight line between two of its cells is clear. A cell is water at least 2 deep (the ships' own rule, see
 * {@code ShipHelper.MIN_DEPTH}) with nothing solid in the two blocks above it. A cell is clear if the 3 x 3 cells round it are water too, so a hull
 * fits; the cells near the start and the goal are allowed to be next to the shore. Chunks that are not loaded count as land.
 */
final class DockRoute {
    private static final int MARGIN = 8;

    private DockRoute() {}

    /** The y of the top water block of the column, scanning down from {@code refY + 3}, or {@link Integer#MIN_VALUE} if there is none. */
    static int surfaceY(Level level, int x, int z, int refY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = refY + 3; y >= refY - 8; y--) {
            pos.set(x, y, z);
            if (level.getFluidState(pos).is(FluidTags.WATER)) {
                return y;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty()) {
                return Integer.MIN_VALUE; // a block in the way before any water
            }
        }
        return Integer.MIN_VALUE;
    }

    /** True if a ship floats here: loaded, water 2 or more deep, and open above the surface. */
    static boolean deepWater(Level level, int x, int z, int refY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, refY, z);
        if (!level.isLoaded(pos)) {
            return false;
        }
        int y = surfaceY(level, x, z, refY);
        if (y == Integer.MIN_VALUE) {
            return false;
        }
        if (!level.getFluidState(pos.set(x, y - 1, z)).is(FluidTags.WATER)) {
            return false;
        }
        for (int up = 1; up <= 2; up++) {
            pos.set(x, y + up, z);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** True if the cell and the 8 cells round it are deep water. */
    static boolean clear(Level level, int x, int z, int refY) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!deepWater(level, x + dx, z + dz, refY)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The waypoints from {@code from} to the target, the last one in the target's cell or a cell next to it; null if there is no way. {@code center} and
     * {@code range} bound the search (the dock and its range).
     */
    @Nullable
    static List<Vec3> plan(Level level, Vec3 from, BlockPos target, BlockPos center, int range, int refY) {
        int radius = range + MARGIN;
        int size = radius * 2 + 1;
        int startX = (int) Math.floor(from.x);
        int startZ = (int) Math.floor(from.z);
        if (Math.abs(startX - center.getX()) > radius || Math.abs(startZ - center.getZ()) > radius
                || Math.abs(target.getX() - center.getX()) > radius || Math.abs(target.getZ() - center.getZ()) > radius) {
            return null;
        }
        byte[] known = new byte[size * size]; // 0 unknown, 1 passable, 2 not
        int[] parent = new int[size * size];
        boolean[] seen = new boolean[size * size];
        int startIndex = index(startX, startZ, center, radius, size);
        int targetX = target.getX();
        int targetZ = target.getZ();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(startIndex);
        seen[startIndex] = true;
        parent[startIndex] = -1;
        int goal = -1;
        int[] dx = {1, -1, 0, 0, 1, 1, -1, -1};
        int[] dz = {0, 0, 1, -1, 1, -1, 1, -1};
        int visited = 0;
        while (!queue.isEmpty() && visited < size * size) {
            int current = queue.poll();
            visited++;
            int cx = center.getX() - radius + current % size;
            int cz = center.getZ() - radius + current / size;
            if (Math.abs(cx - targetX) <= 1 && Math.abs(cz - targetZ) <= 1) {
                goal = current;
                break;
            }
            for (int i = 0; i < 8; i++) {
                int nx = cx + dx[i];
                int nz = cz + dz[i];
                if (Math.abs(nx - center.getX()) > radius || Math.abs(nz - center.getZ()) > radius) {
                    continue;
                }
                int next = index(nx, nz, center, radius, size);
                if (seen[next]) {
                    continue;
                }
                if (i >= 4 && !(passable(level, known, index(cx + dx[i], cz, center, radius, size), cx + dx[i], cz, startX, startZ, targetX, targetZ, refY)
                        && passable(level, known, index(cx, cz + dz[i], center, radius, size), cx, cz + dz[i], startX, startZ, targetX, targetZ, refY))) {
                    continue; // no cutting a corner
                }
                if (!passable(level, known, next, nx, nz, startX, startZ, targetX, targetZ, refY)) {
                    continue;
                }
                seen[next] = true;
                parent[next] = current;
                queue.add(next);
            }
        }
        if (goal < 0) {
            return null;
        }
        List<int[]> cells = new ArrayList<>();
        for (int at = goal; at >= 0; at = parent[at]) {
            cells.add(new int[] {center.getX() - radius + at % size, center.getZ() - radius + at / size});
        }
        Collections.reverse(cells);
        // cut the corners: from each cell on to the farthest one that can be reached in a straight, clear line
        List<Vec3> route = new ArrayList<>();
        int at = 0;
        while (at < cells.size() - 1) {
            int far = at + 1;
            for (int j = cells.size() - 1; j > at + 1; j--) {
                if (lineClear(level, cells.get(at), cells.get(j), startX, startZ, targetX, targetZ, refY)) {
                    far = j;
                    break;
                }
            }
            at = far;
            int[] cell = cells.get(at);
            route.add(new Vec3(cell[0] + 0.5D, from.y, cell[1] + 0.5D));
        }
        if (route.isEmpty()) {
            route.add(new Vec3(target.getX() + 0.5D, from.y, target.getZ() + 0.5D));
        }
        return route;
    }

    private static int index(int x, int z, BlockPos center, int radius, int size) {
        return (z - (center.getZ() - radius)) * size + (x - (center.getX() - radius));
    }

    private static boolean passable(Level level, byte[] known, int index, int x, int z, int startX, int startZ, int targetX, int targetZ, int refY) {
        if (index < 0 || index >= known.length) {
            return false;
        }
        if (known[index] == 0) {
            boolean near = Math.abs(x - startX) <= 3 && Math.abs(z - startZ) <= 3 || Math.abs(x - targetX) <= 3 && Math.abs(z - targetZ) <= 3;
            boolean ok = near ? deepWater(level, x, z, refY) : clear(level, x, z, refY);
            known[index] = (byte) (ok ? 1 : 2);
        }
        return known[index] == 1;
    }

    private static boolean lineClear(Level level, int[] a, int[] b, int startX, int startZ, int targetX, int targetZ, int refY) {
        double length = Math.sqrt((double) (b[0] - a[0]) * (b[0] - a[0]) + (double) (b[1] - a[1]) * (b[1] - a[1]));
        int steps = (int) Math.ceil(length * 2.0D);
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.floor(a[0] + 0.5D + (b[0] - a[0]) * t);
            int z = (int) Math.floor(a[1] + 0.5D + (b[1] - a[1]) * t);
            boolean near = Math.abs(x - startX) <= 3 && Math.abs(z - startZ) <= 3 || Math.abs(x - targetX) <= 3 && Math.abs(z - targetZ) <= 3;
            if (!(near ? deepWater(level, x, z, refY) : clear(level, x, z, refY))) {
                return false;
            }
        }
        return true;
    }
}
