/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/pathing/PathFinderCrawler.
 */
package io.github.capttracker.ancientwarfarevehicle.pathing;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * A greedy wall-following crawler that finds a rough path quickly (the navigator runs it first with a range of 8).
 * Dormant, see the port notes.
 */
public class PathFinderCrawler {
    /**
     * All working set nodes, both open and closed. Used to prevent spurious object creation and to keep already
     * visited nodes cached.
     */
    private final ArrayList<CrawlNode> allNodes = new ArrayList<>();
    @Nullable
    private CrawlNode currentNode;
    final LinkedList<Node> path = new LinkedList<>();

    // start and target points
    int sx;
    int sy;
    int sz;
    int tx;
    int ty;
    int tz;
    int maxRange;
    @Nullable
    PathWorldAccess world;

    public List<Node> findPath(PathWorldAccess world, int x, int y, int z, BlockPos target, int maxRange) {
        this.world = world;
        this.sx = this.cx = x;
        this.sy = this.cy = y;
        this.sz = this.cz = z;
        this.tx = target.getX();
        this.ty = target.getY();
        this.tz = target.getZ();
        this.maxRange = maxRange;
        this.currentNode = getOrMakeNode(sx, sy, sz);
        this.path.clear();
        this.searchLoop();
        LinkedList<Node> foundPath = new LinkedList<>();
        for (int i = 0; i < this.path.size(); i++) {
            // loop from the beginning to the end; in the inner loop from the end down to the current node: if the
            // current node can see the inner node, skip everything in between, else add the current node
            Node n = this.path.get(i);
            for (int k = this.path.size() - 1; k > i; k--) {
                Node p = this.path.get(k);
                if (PathUtils.canPathStraightToTarget(world, n.x, n.y, n.z, new BlockPos(p.x, p.y, p.z))) {
                    i = k - 1; // so the next i run starts where we found it
                    break;
                }
            }
            foundPath.add(new Node(n.x, n.y, n.z));
        }
        if (cx == tx && cy == ty && cz == tz) {
            foundPath.add(new Node(tx, ty, tz));
        }
        this.currentNode = null;
        this.world = null;
        this.allNodes.clear();
        this.path.clear();
        return foundPath;
    }

    int cx;
    int cy;
    int cz;
    /** the current move test direction */
    int dx;
    int dy;
    int dz;
    int pdx;
    int pdy;
    int pdz;
    int pcx;
    int pcy;
    int pcz;
    /** the goal direction */
    int gx;
    int gy;
    int gz;
    int xDiff;
    int yDiff;
    int zDiff;
    boolean followingWall = false;
    /** right turn, may toggle to left in the future */
    int turnDir = 1;

    protected void setInitialDirection() {
        if (Math.abs(xDiff) > Math.abs(zDiff)) {
            // pick a single direction to move, no diagonals
            dx = gx;
            dz = 0;
            if (!world.isWalkable(cx + dx, cy, cz) && !world.isWalkable(cx + dx, cy + 1, cz) && !world.isWalkable(cx, cy - 1, cz)) {
                dx = 0;
                dz = gz;
            }
        } else {
            dz = gz;
            dx = 0;
            if (!world.isWalkable(cx, cy, cz + dz) && !world.isWalkable(cx, cy + 1, cz + dz) && !world.isWalkable(cx, cy - 1, cz + dz)) {
                dz = 0;
                dx = gx;
            }
        }
    }

    protected void searchLoop() {
        calcTargetDirection();
        this.setInitialDirection();
        dy = 0;
        if (dx == 0 && dz == 0) {
            this.path.add(currentNode);
            return;
        }
        for (int i = 0; i < this.maxRange && (cx != tx || cy != ty || cz != tz); i++) {
            this.path.add(currentNode);
            pdx = dx;
            pdy = dy;
            pdz = dz;
            pcx = cx;
            pcy = cy;
            pcz = cz;
            if (tryPathDirectlyToTarget()) {
                break;
            }
            if (tryMoveTowardsGoal()) {
                continue;
            }
            if (tryFollowWall()) {
                continue;
            }
            dx = pdx;
            dz = pdz;
            dy = pdy;
            if (tryMoveStraight()) {
                continue;
            }
            if (tryTurn()) {
                continue;
            }
            if (!tryFindLastTurn()) {
                break;
            }
            if (dx == 0 && dy == 0 && dz == 0) {
                // could not move, or tried moving nowhere
                break;
            }
            if (pcx == cx && pcy == cy && pcz == cz) {
                // did not move
                break;
            }
        }
        if (cx == tx && cy == ty && cz == tz) {
            path.add(new Node(tx, ty, tz));
        }
    }

    protected boolean tryMoveTowardsGoal() {
        this.calcTargetDirection();
        if (Math.abs(xDiff) > Math.abs(zDiff)) {
            // pick a single direction to move, no diagonals
            dx = gx;
            dz = 0;
            if (dx != 0 && tryMoveStraight()) {
                return true;
            }
        } else {
            dz = gz;
            dx = 0;
            if (dz != 0 && tryMoveStraight()) {
                return true;
            }
        }
        return false;
    }

    /** If following a wall, try to turn opposite of the preferred turn direction (disabled in the original). */
    protected boolean tryFollowWall() {
        return false;
    }

    protected boolean tryMoveStraight() {
        if (world.isWalkable(cx, cy + 1, cz) && ty > cy) {
            dy = 1;
            cy += dy;
            this.currentNode = getOrMakeNode(cx, cy, cz);
            return true;
        }
        if (world.isWalkable(cx, cy - 1, cz) && ty < cy) {
            dy = -1;
            cy += dy;
            this.currentNode = getOrMakeNode(cx, cy, cz);
            return true;
        }
        if (world.isWalkable(cx + dx, cy, cz + dz)) {
            dy = 0;
            cx += dx;
            cz += dz;
            cy += dy;
            this.currentNode = getOrMakeNode(cx, cy, cz);
            return true;
        }
        if (world.isWalkable(cx + dx, cy + 1, cz + dz) && !world.checkBlockBounds(cx, cy + 2, cz)) {
            dy = 1;
            cx += dx;
            cz += dz;
            cy += dy;
            this.currentNode = getOrMakeNode(cx, cy, cz);
            return true;
        }
        if (world.isWalkable(cx + dx, cy - 1, cz + dz) && !world.checkBlockBounds(cx + dx, cy + 1, cz + dz)) {
            dy = -1;
            cx += dx;
            cz += dz;
            cy += dy;
            this.currentNode = getOrMakeNode(cx, cy, cz);
            return true;
        }
        return false;
    }

    protected boolean tryTurn() {
        dx = pdx;
        dz = pdz;
        if (dx != 0) {
            // collided on x: set x to 0, find z
            dx = 0;
            dz = getZforTurn();
            if (tryMoveStraight()) {
                followingWall = true;
                return true;
            }
        } else if (dz != 0) {
            // collided on z: set z to 0, find x
            dz = 0;
            dx = getXforTurn();
            if (tryMoveStraight()) {
                followingWall = true;
                return true;
            }
        }
        return false;
    }

    /** Called when moving in the z direction, to turn left or right. */
    protected int getXforTurn() {
        if (pcz < cz) {
            return turnDir;
        } else if (pcz > cz) {
            return -turnDir;
        }
        return 0;
    }

    protected int getZforTurn() {
        if (pcx < cx) {
            return turnDir;
        } else if (pcx > cx) {
            return -turnDir;
        }
        return 0;
    }

    protected boolean tryFindLastTurn() {
        return false;
    }

    /** Disabled in the original: checking every node to the goal is too costly (3 ms to 11 ms per 100 runs). */
    protected boolean tryPathDirectlyToTarget() {
        return false;
    }

    protected void calcTargetDirection() {
        gx = tx - cx;
        gy = ty - cy;
        gz = tz - cz;
        xDiff = gx;
        yDiff = gy;
        zDiff = gz;
        gx = Integer.signum(gx);
        gy = Integer.signum(gy);
        gz = Integer.signum(gz);
    }

    private CrawlNode getOrMakeNode(int x, int y, int z) {
        for (CrawlNode c : this.allNodes) {
            if (c.equals(x, y, z)) {
                return c;
            }
        }
        CrawlNode n = new CrawlNode(x, y, z);
        allNodes.add(n);
        return n;
    }

    private static class CrawlNode extends Node {
        public CrawlNode(int bX, int bY, int bZ) {
            super(bX, bY, bZ);
        }
    }
}
