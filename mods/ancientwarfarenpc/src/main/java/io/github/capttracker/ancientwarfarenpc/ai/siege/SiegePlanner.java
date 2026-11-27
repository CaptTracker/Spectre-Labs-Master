/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): the ground route of a tower, a mantlet or a ram.
 */
package io.github.capttracker.ancientwarfarenpc.ai.siege;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A grid search (A star over 1 x 1 columns, eight directions) for a vehicle that has to move over open ground: it climbs nothing (the siege works have a step height of
 * 0) and only drops one block at a time. A cell is free when the whole footprint of the vehicle (a square of {@code radius} columns round the cell, the corners cut off from
 * radius 2 on, so a turning vehicle has room) has no collision from the cell's level up to {@code height} blocks and no fluid, and the middle column stands on something. The columns are
 * tested once and remembered.
 *
 * <p>The search is spread over ticks: {@link #step} does a number of expansions and says whether it is still running. A goal that cannot be reached exactly ends at the closest
 * cell that was reached, if that is within 4 blocks of it; any other failure is {@link Status#FAILED}. The route is cut down to waypoints that are in sight of each other.
 */
public final class SiegePlanner {
    public enum Status { RUNNING, FOUND, FAILED }

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int NONE = Integer.MIN_VALUE;
    /** How many cells ahead a shortcut is looked for. */
    private static final int SHORTCUT_SPAN = 24;

    private static final class Node {
        final int x;
        final int y;
        final int z;
        final double g;
        final double f;
        final Node parent;
        boolean closed;

        Node(int x, int y, int z, double g, double h, Node parent) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.g = g;
            this.f = g + h;
            this.parent = parent;
        }
    }

    private final Level level;
    private final int height;
    private final int startX;
    private final int startZ;
    private final int goalX;
    private final int goalZ;
    private final double goalPx;
    private final double goalPz;
    private final int maxNodes;
    private final int range;
    private final int[][] footprint;
    private final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble((Node n) -> n.f));
    private final Map<Long, Node> nodes = new HashMap<>();
    private final Map<Long, Boolean> clearCache = new HashMap<>();
    private int expanded = 0;
    private Status status = Status.RUNNING;
    private Node best;
    private List<Vec3> route = List.of();

    /**
     * @param radius the footprint: 1 is a 3 x 3 square, 2 a 5 x 5 square without its corners
     * @param height blocks of free room over the ground
     */
    public SiegePlanner(Level level, int radius, int height, Vec3 start, int startY, Vec3 goal, int maxNodes, int range) {
        this.level = level;
        this.height = Math.max(1, height);
        this.startX = Mth.floor(start.x);
        this.startZ = Mth.floor(start.z);
        this.goalX = Mth.floor(goal.x);
        this.goalZ = Mth.floor(goal.z);
        this.goalPx = goal.x;
        this.goalPz = goal.z;
        this.maxNodes = maxNodes;
        this.range = range;
        List<int[]> cells = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (radius >= 2 && Math.abs(dx) == radius && Math.abs(dz) == radius) {
                    continue;
                }
                cells.add(new int[] {dx, dz});
            }
        }
        this.footprint = cells.toArray(new int[0][]);
        Node first = new Node(startX, startY, startZ, 0.0D, heuristic(startX, startZ), null);
        nodes.put(key(startX, startZ), first);
        open.add(first);
    }

    public Status status() {
        return status;
    }

    /** The waypoints (cell middles, the exact goal last when it was reached), without the start. Valid when the status is FOUND. */
    public List<Vec3> route() {
        return route;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private double heuristic(int x, int z) {
        double dx = Math.abs(x - goalX);
        double dz = Math.abs(z - goalZ);
        return (dx + dz) + (Math.sqrt(2.0D) - 2.0D) * Math.min(dx, dz);
    }

    /** Does up to {@code budget} expansions. */
    public Status step(int budget) {
        while (status == Status.RUNNING && budget-- > 0) {
            Node cur = open.poll();
            if (cur == null) {
                finishPartial();
                break;
            }
            if (cur.closed || nodes.get(key(cur.x, cur.z)) != cur) {
                continue;
            }
            cur.closed = true;
            expanded++;
            if (Math.abs(cur.x - goalX) <= 1 && Math.abs(cur.z - goalZ) <= 1) {
                build(cur, true);
                break;
            }
            if (best == null || heuristic(cur.x, cur.z) < heuristic(best.x, best.z)) {
                best = cur;
            }
            if (expanded > maxNodes) {
                finishPartial();
                break;
            }
            for (int[] d : DIRS) {
                int nx = cur.x + d[0];
                int nz = cur.z + d[1];
                if (Math.abs(nx - startX) > range || Math.abs(nz - startZ) > range) {
                    continue;
                }
                Node known = nodes.get(key(nx, nz));
                if (known != null && known.closed) {
                    continue;
                }
                int ny = levelAt(nx, nz, cur.y);
                if (ny == NONE) {
                    continue;
                }
                double g = cur.g + (d[0] != 0 && d[1] != 0 ? 1.4142D : 1.0D) + (ny != cur.y ? 0.5D : 0.0D);
                if (known == null || g < known.g) {
                    Node node = new Node(nx, ny, nz, g, heuristic(nx, nz), cur);
                    nodes.put(key(nx, nz), node);
                    open.add(node);
                }
            }
        }
        return status;
    }

    private void finishPartial() {
        if (best != null && heuristic(best.x, best.z) <= 4.0D) {
            build(best, false);
        } else {
            status = Status.FAILED;
        }
    }

    private void build(Node end, boolean exact) {
        List<Vec3> points = new ArrayList<>();
        List<Integer> levels = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) {
            points.add(new Vec3(n.x + 0.5D, n.y, n.z + 0.5D));
            levels.add(n.y);
        }
        Collections.reverse(points);
        Collections.reverse(levels);
        if (exact) {
            points.add(new Vec3(goalPx, end.y, goalPz));
            levels.add(end.y);
        }
        List<Vec3> out = new ArrayList<>();
        int i = 0;
        while (i < points.size() - 1) {
            int j = Math.min(points.size() - 1, i + SHORTCUT_SPAN);
            while (j > i + 1 && !lineClear(points.get(i), points.get(j), levels.get(i))) {
                j--;
            }
            out.add(points.get(j));
            i = j;
        }
        route = out;
        status = Status.FOUND;
    }

    private boolean lineClear(Vec3 a, Vec3 b, int y) {
        double length = Math.hypot(b.x - a.x, b.z - a.z);
        int steps = Math.max(1, (int) Math.ceil(length / 0.75D));
        int current = y;
        for (int s = 1; s < steps; s++) {
            double t = s / (double) steps;
            int ny = levelAt(Mth.floor(a.x + (b.x - a.x) * t), Mth.floor(a.z + (b.z - a.z) * t), current);
            if (ny == NONE) {
                return false;
            }
            current = ny;
        }
        return true;
    }

    /** The level the vehicle would stand on in the column coming from {@code y}: the same, or one lower, or NONE. */
    private int levelAt(int x, int z, int y) {
        if (footprintFree(x, z, y) && supported(x, y - 1, z)) {
            return y;
        }
        if (footprintFree(x, z, y - 1) && supported(x, y - 2, z)) {
            return y - 1;
        }
        return NONE;
    }

    private boolean footprintFree(int x, int z, int y) {
        for (int[] c : footprint) {
            if (!columnClear(x + c[0], y, z + c[1])) {
                return false;
            }
        }
        return true;
    }

    private boolean columnClear(int x, int y, int z) {
        long k = new BlockPos(x, y, z).asLong();
        Boolean cached = clearCache.get(k);
        if (cached != null) {
            return cached;
        }
        boolean clear = true;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int h = 0; h < height && clear; h++) {
            m.set(x, y + h, z);
            if (!level.isLoaded(m)) {
                clear = false;
                break;
            }
            BlockState state = level.getBlockState(m);
            clear = state.getCollisionShape(level, m).isEmpty() && state.getFluidState().isEmpty();
        }
        clearCache.put(k, clear);
        return clear;
    }

    private boolean supported(int x, int y, int z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, y, z);
        return level.isLoaded(m) && !level.getBlockState(m).getCollisionShape(level, m).isEmpty();
    }
}
