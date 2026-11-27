/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): the water route planner of the fishing dock, made general for the sea trade (any ship width, long routes, partial routes).
 */
package io.github.capttracker.ancientwarfarevehicle.entity.ship;

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
 * Finds the waypoints a ship steers through over water: a breadth first search over the cells (1 x 1 blocks) of the water, then the path is cut short wherever
 * a straight line between two of its cells is clear. A cell is water at least {@link ShipHelper#MIN_DEPTH} deep (the ships' own rule) with nothing solid in the
 * two blocks above it. A cell is clear if the cells within {@code clearance} blocks of it (a 3 x 3 square for clearance 1, 5 x 5 for 2) are water too, so a hull
 * fits; the cells near the start and the goal are allowed to be next to the shore. Chunks that are not loaded count as land: a route only runs over loaded water.
 *
 * <p>The fishing dock uses it with clearance 1 ({@code DockRoute}); the sea trade of rework phase 11 uses it for the cog (clearance 2) over routes of a few hundred
 * blocks, and asks for a partial route when the goal lies beyond the loaded chunks: the ship then sails to the loaded cell nearest to the goal.
 */
public final class ShipRoutePlanner {
    /** The blocks added to the search area round the start and the goal. */
    public static final int MARGIN = 8;
    /** The largest search area (blocks from its middle to its edge); a longer route is not planned. */
    public static final int MAX_RADIUS = 200;

    private ShipRoutePlanner() {}

    /** A planned route: the waypoints (water positions), whether the last one is at the goal, and its length in blocks. */
    public record Plan(List<Vec3> route, boolean complete, double length) {}

    /** The y of the top water block of the column, scanning down from {@code refY + 3}, or {@link Integer#MIN_VALUE} if there is none. */
    public static int surfaceY(Level level, int x, int z, int refY) {
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
    public static boolean deepWater(Level level, int x, int z, int refY) {
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
    public static boolean clear(Level level, int x, int z, int refY) {
        return clear(level, x, z, refY, 1);
    }

    /** True if every cell within {@code clearance} blocks of the cell (a square) is deep water. */
    public static boolean clear(Level level, int x, int z, int refY, int clearance) {
        for (int dx = -clearance; dx <= clearance; dx++) {
            for (int dz = -clearance; dz <= clearance; dz++) {
                if (!deepWater(level, x + dx, z + dz, refY)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** How many water blocks lie under the surface block of the column (up to 32), 0 if the column has no water near {@code refY}. */
    public static int waterDepth(Level level, int x, int z, int refY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, refY, z);
        if (!level.isLoaded(pos)) {
            return 0;
        }
        int y = surfaceY(level, x, z, refY);
        if (y == Integer.MIN_VALUE) {
            return 0;
        }
        int depth = 0;
        while (depth < 32 && level.getFluidState(pos.set(x, y - depth, z)).is(FluidTags.WATER)) {
            depth++;
        }
        return depth;
    }

    /**
     * The water cell (the top water block) nearest to {@code around} within {@code radius} blocks where a hull with this clearance floats in water at least
     * {@code minDepth} deep; null if there is none. {@code refY} is the height to look for the surface at.
     */
    @Nullable
    public static BlockPos findOpenWater(Level level, BlockPos around, int radius, int refY, int clearance, int minDepth) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distance = dx * dx + dz * dz;
                if (distance >= bestDistance) {
                    continue;
                }
                int x = around.getX() + dx;
                int z = around.getZ() + dz;
                if (!clear(level, x, z, refY, clearance) || waterDepth(level, x, z, refY) < minDepth) {
                    continue;
                }
                best = new BlockPos(x, surfaceY(level, x, z, refY), z);
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * The waypoints from {@code from} to the target, the last one in the target's cell or a cell next to it; null if there is no way. {@code center} and
     * {@code radius} bound the search (blocks from the centre to the edge of the square; both ends must lie inside it).
     */
    @Nullable
    public static List<Vec3> plan(Level level, Vec3 from, BlockPos target, BlockPos center, int radius, int refY, int clearance) {
        Plan plan = plan(level, from, target, center, radius, refY, clearance, false);
        return plan == null ? null : plan.route();
    }

    /**
     * A route between two points of any distance up to twice {@link #MAX_RADIUS}: the search area is the square that holds both ends with the margin. Null if
     * the points are too far apart, or there is no way (and {@code partial} is off).
     */
    @Nullable
    public static Plan planBetween(Level level, Vec3 from, BlockPos target, int refY, int clearance, boolean partial) {
        int startX = (int) Math.floor(from.x);
        int startZ = (int) Math.floor(from.z);
        int centerX = (startX + target.getX()) / 2;
        int centerZ = (startZ + target.getZ()) / 2;
        int radius = Math.max(Math.abs(startX - target.getX()), Math.abs(startZ - target.getZ())) / 2 + MARGIN;
        if (radius > MAX_RADIUS) {
            return null;
        }
        return plan(level, from, target, new BlockPos(centerX, refY, centerZ), radius, refY, clearance, partial);
    }

    /**
     * As {@link #plan(Level, Vec3, BlockPos, BlockPos, int, int, int)} but with a {@link Plan} result. With {@code partial} set, a goal that cannot be reached
     * gives the route to the reached cell that is nearest to the goal ({@code complete} false), or null if the ship cannot get any nearer than it is.
     */
    @Nullable
    public static Plan plan(Level level, Vec3 from, BlockPos target, BlockPos center, int radius, int refY, int clearance, boolean partial) {
        int size = radius * 2 + 1;
        int startX = (int) Math.floor(from.x);
        int startZ = (int) Math.floor(from.z);
        if (Math.abs(startX - center.getX()) > radius || Math.abs(startZ - center.getZ()) > radius
                || Math.abs(target.getX() - center.getX()) > radius || Math.abs(target.getZ() - center.getZ()) > radius) {
            return null;
        }
        Grid grid = new Grid(level, center, radius, size, refY, clearance, startX, startZ, target.getX(), target.getZ());
        int[] parent = new int[size * size];
        boolean[] seen = new boolean[size * size];
        int startIndex = grid.index(startX, startZ);
        int targetX = target.getX();
        int targetZ = target.getZ();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(startIndex);
        seen[startIndex] = true;
        parent[startIndex] = -1;
        int goal = -1;
        int nearest = startIndex;
        long nearestSq = distSq(startX, startZ, targetX, targetZ);
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
            long sq = distSq(cx, cz, targetX, targetZ);
            if (sq < nearestSq) {
                nearestSq = sq;
                nearest = current;
            }
            for (int i = 0; i < 8; i++) {
                int nx = cx + dx[i];
                int nz = cz + dz[i];
                if (Math.abs(nx - center.getX()) > radius || Math.abs(nz - center.getZ()) > radius) {
                    continue;
                }
                int next = grid.index(nx, nz);
                if (seen[next]) {
                    continue;
                }
                if (i >= 4 && !(grid.passable(cx + dx[i], cz) && grid.passable(cx, cz + dz[i]))) {
                    continue; // no cutting a corner
                }
                if (!grid.passable(nx, nz)) {
                    continue;
                }
                seen[next] = true;
                parent[next] = current;
                queue.add(next);
            }
        }
        boolean complete = goal >= 0;
        if (!complete) {
            if (!partial || nearest == startIndex) {
                return null;
            }
            goal = nearest;
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
                if (grid.lineClear(cells.get(at), cells.get(j))) {
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
        double length = 0.0D;
        Vec3 previous = from;
        for (Vec3 point : route) {
            length += Math.hypot(point.x - previous.x, point.z - previous.z);
            previous = point;
        }
        return new Plan(route, complete, length);
    }

    private static long distSq(int ax, int az, int bx, int bz) {
        long dx = ax - bx;
        long dz = az - bz;
        return dx * dx + dz * dz;
    }

    /** The cells of one search: the deep water test is cached for every column of the area. */
    private static final class Grid {
        private final Level level;
        private final BlockPos center;
        private final int radius;
        private final int size;
        private final int refY;
        private final int clearance;
        private final int startX;
        private final int startZ;
        private final int targetX;
        private final int targetZ;
        private final byte[] deep; // 0 unknown, 1 deep water, 2 not
        private final byte[] known; // 0 unknown, 1 passable, 2 not

        Grid(Level level, BlockPos center, int radius, int size, int refY, int clearance, int startX, int startZ, int targetX, int targetZ) {
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.size = size;
            this.refY = refY;
            this.clearance = clearance;
            this.startX = startX;
            this.startZ = startZ;
            this.targetX = targetX;
            this.targetZ = targetZ;
            this.deep = new byte[size * size];
            this.known = new byte[size * size];
        }

        int index(int x, int z) {
            return (z - (center.getZ() - radius)) * size + (x - (center.getX() - radius));
        }

        private boolean inside(int x, int z) {
            return Math.abs(x - center.getX()) <= radius && Math.abs(z - center.getZ()) <= radius;
        }

        private boolean deepAt(int x, int z) {
            if (!inside(x, z)) {
                return deepWater(level, x, z, refY);
            }
            int index = index(x, z);
            if (deep[index] == 0) {
                deep[index] = (byte) (deepWater(level, x, z, refY) ? 1 : 2);
            }
            return deep[index] == 1;
        }

        private boolean clearAt(int x, int z) {
            for (int dx = -clearance; dx <= clearance; dx++) {
                for (int dz = -clearance; dz <= clearance; dz++) {
                    if (!deepAt(x + dx, z + dz)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean near(int x, int z) {
            return Math.abs(x - startX) <= 3 && Math.abs(z - startZ) <= 3 || Math.abs(x - targetX) <= 3 && Math.abs(z - targetZ) <= 3;
        }

        boolean passable(int x, int z) {
            if (!inside(x, z)) {
                return false;
            }
            int index = index(x, z);
            if (known[index] == 0) {
                boolean ok = near(x, z) ? deepAt(x, z) : clearAt(x, z);
                known[index] = (byte) (ok ? 1 : 2);
            }
            return known[index] == 1;
        }

        boolean lineClear(int[] a, int[] b) {
            double length = Math.sqrt((double) (b[0] - a[0]) * (b[0] - a[0]) + (double) (b[1] - a[1]) * (b[1] - a[1]));
            int steps = (int) Math.ceil(length * 2.0D);
            for (int i = 1; i < steps; i++) {
                double t = (double) i / steps;
                int x = (int) Math.floor(a[0] + 0.5D + (b[0] - a[0]) * t);
                int z = (int) Math.floor(a[1] + 0.5D + (b[1] - a[1]) * t);
                if (!(near(x, z) ? deepAt(x, z) : clearAt(x, z))) {
                    return false;
                }
            }
            return true;
        }
    }
}
