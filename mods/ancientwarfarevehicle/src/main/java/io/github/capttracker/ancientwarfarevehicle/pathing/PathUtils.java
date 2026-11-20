/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/pathing/PathUtils.
 */
package io.github.capttracker.ancientwarfarevehicle.pathing;

import io.github.capttracker.ancientwarfare.util.Trig;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Line-of-sight tests (integer Bresenham), the guided crawl and the closest pathable position search.
 * Sources of a few line plotting algorithms: playtechs.blogspot.com/2007/03/raytracing-on-grid.html,
 * xnawiki.com/index.php/Voxel_traversal and www.cse.yorku.ca/~amana/research/grid.pdf.
 */
public class PathUtils {
    private PathUtils() {}

    public static Node getClosestPathableTo(PathWorldAccess world, int x, int y, int z, int maxH, int maxV, int ox, int oy, int oz) {
        Node closest = new Node(x, y, z);
        float closeDist = Float.POSITIVE_INFINITY;
        float tcloseDist = Float.POSITIVE_INFINITY;
        for (int y1 = y - maxV; y1 <= y + maxV; y1++) {
            for (int x1 = x - maxH; x1 <= x + maxH; x1++) {
                for (int z1 = z - maxH; z1 <= z + maxH; z1++) {
                    if (world.isWalkable(x1, y1, z1)) {
                        float dist = Trig.getDistance(x, y, z, x1, y1, z1);
                        float tdist = Trig.getDistance(ox, oy, oz, x1, y1, z1);
                        if (dist < closeDist && tdist < tcloseDist) {
                            closeDist = dist;
                            tcloseDist = tdist;
                            closest.reassign(x1, y1, z1);
                        }
                    }
                }
            }
        }
        return closest;
    }

    /** "uhh..yah...its freaky...but it finds paths (most of the time), FAST (all of the time)" (original comment). */
    public static List<Node> guidedCrawl(PathWorldAccess world, int ex, int ey, int ez, int tx, int ty, int tz, int numOfNodes, Random rng) {
        List<Node> nodes = new ArrayList<>();
        // get the initial move direction towards the target, add the start node to the path, then for each node
        // find the next move direction from the current direction and the direction to the target
        int cx = ex;
        int cy = ey;
        int cz = ez;
        int mx = tx - ex;
        int mz = tz - ez;
        mx = mx < 0 ? -1 : mx > 1 ? 1 : mx;
        mz = mz < 0 ? -1 : mz > 1 ? 1 : mz;
        int my = 0;
        int dx = mx;
        int dz = mz;
        int dy = my;
        nodes.add(new Node(cx, cy, cz));
        for (int i = 0; i < numOfNodes; i++) {
            // the original compares the target x with the current y here (sic); kept
            if (tx < cy && world.isWalkable(cx, cy - 1, cz)) {
                cy--;
                nodes.add(new Node(cx, cy, cz));
            } else if (tx > cy && world.isWalkable(cx, cy + 1, cz)) {
                cy++;
                nodes.add(new Node(cx, cy, cz));
            } else if (world.isWalkable(cx + mx, cy + dy, cz + mz)) {
                dx = mx;
                dz = mz;
                cx += dx;
                cy += dy;
                cz += dz;
                nodes.add(new Node(cx, cy, cz));
            } else if (world.isWalkable(cx + dx, cy + dy, cz + dz)) {
                cx += dx;
                cy += dy;
                cz += dz;
                nodes.add(new Node(cx, cy, cz));
            } else if (world.isWalkable(cx + dx, cy, cz + dz)) {
                dy = 0;
                cx += dx;
                cy += dy;
                cz += dz;
                nodes.add(new Node(cx, cy, cz));
            } else if (world.isWalkable(cx + dx, cy - 1, cz + dz)) {
                dy = -1;
                cx += dx;
                cy += dy;
                cz += dz;
                nodes.add(new Node(cx, cy, cz));
            } else if (world.isWalkable(cx + dx, cy + 1, cz + dz)) {
                dy = 1;
                cx += dx;
                cy += dy;
                cz += dz;
                nodes.add(new Node(cx, cy, cz));
            } else {
                int turn = getRotationTowardTarget(dx, dz, mx, mz);
                int[] offset;
                if (turn <= 0) {
                    offset = getRotatedMoveDelta(dx, dz, -1);
                } else {
                    offset = getRotatedMoveDelta(dx, dz, 1);
                }
                if (world.isWalkable(cx + offset[0], cy, cz + offset[1])) {
                    dx = offset[0];
                    dz = offset[1];
                    dy = 0;
                    cx += dx;
                    cz += dz;
                    cy += dy;
                    nodes.add(new Node(cx, cy, cz));
                } else if (world.isWalkable(cx + offset[0], cy - 1, cz + offset[1])) {
                    dx = offset[0];
                    dz = offset[1];
                    dy = -1;
                    cx += dx;
                    cz += dz;
                    cy += dy;
                    nodes.add(new Node(cx, cy, cz));
                } else if (world.isWalkable(cx + offset[0], cy + 1, cz + offset[1])) {
                    dx = offset[0];
                    dz = offset[1];
                    dy = 1;
                    cx += dx;
                    cz += dz;
                    cy += dy;
                    nodes.add(new Node(cx, cy, cz));
                }
            }
            mx = tx - cx;
            mz = tz - cz;
            mx = mx < 0 ? -1 : mx > 1 ? 1 : mx;
            mz = mz < 0 ? -1 : mz > 1 ? 1 : mz;
            if (cx == tx && cy == ty && cz == tz) {
                break;
            }
        }
        return nodes;
    }

    private static int getRotationTowardTarget(int dx, int dz, int mx, int mz) {
        boolean foundCurrent = false;
        boolean foundBase = false;
        int currentOffset = 0;
        int baseOffset = 0;
        for (int i = 0; i < OFFSETS.length; i++) {
            int[] offset = OFFSETS[i];
            if (offset[0] == dx && offset[1] == dz) {
                currentOffset = i;
                foundCurrent = true;
            }
            if (offset[0] == mx && offset[1] == mz) {
                baseOffset = i;
                foundBase = true;
            }
            if (foundBase && foundCurrent) {
                break;
            }
        }
        return currentOffset - baseOffset;
    }

    private static int[] getRotatedMoveDelta(int dx, int dz, int turnAmt) {
        while (turnAmt < 0) {
            turnAmt += 8;
        }
        int offsetNum = 0;
        for (int i = 0; i < OFFSETS.length; i++) {
            int[] offset = OFFSETS[i];
            if (offset[0] == dx && offset[1] == dz) {
                offsetNum = i;
                break;
            }
        }
        for (int i = 0; i < turnAmt; i++) {
            offsetNum++;
            if (offsetNum >= OFFSETS.length) {
                offsetNum = 0;
            }
        }
        if (offsetNum < 0 || offsetNum >= OFFSETS.length) {
            offsetNum = 0;
        }
        return OFFSETS[offsetNum];
    }

    private static final int[][] OFFSETS = {
            {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}
    };

    public static boolean canPathStraightToTargetLevel(PathWorldAccess worldAccess, int x0, int ey, int z0, int x1, int ty, int z1) {
        if (ey != ty) {
            return false;
        }
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;
        int e2;
        boolean swim = worldAccess.canSwim;
        worldAccess.canSwim = false;
        boolean canPath = true;
        for (int i = 0; i < dx + dz; i++) {
            BlockPos pos = new BlockPos(x0, ey, z0);
            if (worldAccess.isDoor(pos)) {
                canPath = false;
                break;
            } else if (worldAccess.isLadder(pos)) {
                canPath = false;
                break;
            } else if (!worldAccess.isWalkable(x0, ey, z0)) {
                canPath = false;
                break;
            }
            if (x0 == x1 && z0 == z1) {
                break; // finished
            }
            e2 = 2 * err;
            if (e2 > -dz) {
                err = err - dz;
                x0 = x0 + sx;
            }
            if (e2 < dx) {
                err = err + dx;
                z0 = z0 + sz;
            }
        }
        worldAccess.canSwim = swim;
        return canPath;
    }

    public static boolean canPathStraightToTarget(PathWorldAccess worldAccess, int ex, int ey, int ez, BlockPos target) {
        int yOffset = target.getY() - ey;
        if (Math.abs(yOffset) > 1) {
            return false;
        }
        int dx = Math.abs(target.getX() - ex);
        int dz = Math.abs(target.getZ() - ez);
        int px = ex;
        int pz = ez;
        int sx = ex < target.getX() ? 1 : -1;
        int sz = ez < target.getZ() ? 1 : -1;
        int err = dx - dz;
        int e2;
        int cy = ey;
        boolean swim = worldAccess.canSwim;
        worldAccess.canSwim = false;
        boolean canPath = true;
        for (int i = 0; i < dx + dz; i++) {
            if (worldAccess.isDoor(new BlockPos(ex, cy, ez))) {
                canPath = false;
                break;
            } else if (worldAccess.isLadder(new BlockPos(ex, cy, ey))) {
                // (sic) the original tests the position (ex, cy, ey), not (ex, cy, ez)
                canPath = false;
                break;
            }
            if (worldAccess.isWalkable(ex, cy - 1, ez) && !worldAccess.checkBlockBounds(ex, cy + 1, ez)) {
                cy--;
            } else if (worldAccess.isWalkable(ex, cy, ez)) {
                // stay level
            } else if (worldAccess.isWalkable(ex, cy + 1, ez) && !worldAccess.checkBlockBounds(px, cy + 2, pz)) {
                cy++;
            } else {
                canPath = false;
                break;
            }
            if (Math.abs(cy - target.getY()) > 1) {
                canPath = false;
                break;
            }
            if (ex == target.getX() && ez == target.getZ()) {
                break; // finished
            }
            e2 = 2 * err;
            px = ex;
            pz = ez;
            if (e2 > -dz) {
                err = err - dz;
                ex = ex + sx;
            }
            if (e2 < dx) {
                err = err + dx;
                ez = ez + sz;
            }
        }
        worldAccess.canSwim = swim;
        return canPath;
    }

    /** Bresenham line between two points on the horizontal plane, as block positions at y 0 (http://en.wikipedia.org/wiki/Bresenham%27s_line_algorithm). */
    public static List<BlockPos> getPositionsBetween2(float x0, float z0, float x1, float z1) {
        List<BlockPos> blocks = new ArrayList<>();
        float dx = (int) Math.abs(x1 - x0);
        float dz = (int) Math.abs(z1 - z0);
        float sx = x0 < x1 ? 1 : -1;
        float sz = z0 < z1 ? 1 : -1;
        float err = dx - dz;
        float e2;
        for (int i = 0; i < dx + dz; i++) {
            blocks.add(BlockPos.containing(x0, 0, z0));
            if (x0 == x1 && z0 == z1) {
                break; // finished
            }
            e2 = 2 * err;
            if (e2 > -dz) {
                err = err - dz;
                x0 = x0 + sx;
            }
            if (e2 < dx) {
                err = err + dx;
                z0 = z0 + sz;
            }
        }
        return blocks;
    }

    /** A 4-connected line ("mostly works, returns a few 'odd' hits" in the original). */
    public static List<BlockPos> getPositionsBetween(float x0, float y0, float z0, float x1, float y1, float z1) {
        List<BlockPos> blocks = new ArrayList<>();
        int dx = (int) Math.abs(x1 - x0);
        int dz = (int) Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int e = 0;
        int e1;
        int e2;
        for (int i = 0; i < dx + dz; i++) {
            blocks.add(BlockPos.containing(x0, y0, z0));
            e1 = e + dz;
            e2 = e - dx;
            if (Math.abs(e1) < Math.abs(e2)) {
                x0 += sx;
                e = e1;
            } else {
                z0 += sz;
                e = e2;
            }
        }
        blocks.add(BlockPos.containing(x0, y0, z0));
        return blocks;
    }
}
