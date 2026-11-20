/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/pathing/PathFinderThetaStar.
 */
package io.github.capttracker.ancientwarfarevehicle.pathing;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.PriorityQueue;

/**
 * A theta-star implementation (pre-smoothed A* paths), using line-of-sight checks at every node link to
 * recalculate the optimal g-costs from start to current so that it uses a straight line from A to B directly
 * instead of doing all the diagonal moves and then all the horizontal moves.
 *
 * <p>Dormant: nothing in the original sets a search going (see the port notes).
 */
public class PathFinderThetaStar {
    /** The open list. */
    private final PriorityQueue<Node> qNodes = new PriorityQueue<>();

    /**
     * All working set nodes, both open and closed. Used to prevent spurious object creation, to keep the scores
     * of visited but closed nodes valid and cached, and for pulling live nodes from the open list without having
     * to synchronise them back in.
     */
    private final ArrayList<Node> allNodes = new ArrayList<>();

    /** The current node's neighbors, just a cached list. */
    private final ArrayList<Node> searchNodes = new ArrayList<>();

    @Nullable
    private Node currentNode;

    // start and target points
    int sx;
    int sy;
    int sz;
    int tx;
    int ty;
    int tz;
    int minx;
    int miny;
    int minz;
    int maxx;
    int maxy;
    int maxz;
    int searchBufferRange = 40;
    int maxRange = 80;
    @Nullable
    PathWorldAccess world;
    long startTime;
    long runTime;
    /** 20 ms by default. Public so it may be overridden at run time; must be reset between runs. */
    public long maxRunTime = 20000000L;
    public long maxSearchIterations = 1200;
    @Nullable
    private Node bestEndNode = null;
    private float bestPathLength = 0.f;
    private float bestPathDist = Float.POSITIVE_INFINITY;
    private int searchIteration;
    @Nullable
    IPathableCallback caller = null;

    public boolean isSearching = false;
    protected boolean instantSearch = false;

    public void findPath(PathWorldAccess world, int x, int y, int z, BlockPos target, int maxRange, IPathableCallback caller, boolean instant) {
        this.allNodes.clear();
        this.qNodes.clear();
        this.searchNodes.clear();
        this.world = world;
        this.caller = caller;
        this.instantSearch = instant;
        this.isSearching = true;
        this.sx = x;
        this.sy = y;
        this.sz = z;
        this.tx = target.getX();
        this.ty = target.getY();
        this.tz = target.getZ();
        this.maxRange = maxRange;
        minx = Math.min(x, tx);
        maxx = Math.max(x, tx);
        miny = Math.min(y, ty);
        maxy = Math.max(y, ty);
        minz = Math.min(z, tz);
        maxz = Math.max(z, tz);
        minx -= searchBufferRange;
        maxx += searchBufferRange;
        miny -= searchBufferRange;
        maxy += searchBufferRange;
        minz -= searchBufferRange;
        maxz += searchBufferRange;
        this.startTime = System.nanoTime();
        this.currentNode = getOrMakeNode(sx, sy, sz, null);
        this.currentNode.g = 0;
        this.currentNode.f = this.currentNode.getH(tx, ty, tz);
        this.qNodes.offer(this.currentNode);
        this.bestEndNode = this.currentNode;
        this.bestPathLength = 0;
        this.bestPathDist = Float.POSITIVE_INFINITY;
        this.searchIteration = 0;
        this.runTime = 0;
    }

    protected void onPathFound() {
        LinkedList<Node> path = new LinkedList<>();
        Node n = this.currentNode;
        Node c = null;
        while (n != null) {
            Node p = c;
            c = new Node(n.x, n.y, n.z);
            c.parentNode = p;
            path.push(c);
            n = n.parentNode;
        }
        if (this.caller != null) {
            this.caller.onPathFound(path);
        }
        this.currentNode = null;
        this.world = null;
        this.bestEndNode = null;
        this.allNodes.clear();
        this.qNodes.clear();
        this.searchNodes.clear();
        this.isSearching = false;
    }

    public void doSearchIterations(int num) {
        this.startTime = System.nanoTime();
        if (!isSearching) {
            return;
        }
        for (int i = 0; i < num; i++) {
            if (this.searchLoop()) {
                this.onPathFound();
                this.isSearching = false;
                break;
            }
        }
        this.runTime += System.nanoTime() - this.startTime;
    }

    private boolean searchLoop() {
        PathWorldAccess world = this.world;
        boolean goalWalkable = world.isWalkable(tx, ty, tz) && world.isWalkable(tx, ty + 1, tz);
        this.searchIteration++;
        if (this.qNodes.isEmpty()) {
            return true;
        }
        Node current = this.qNodes.poll();
        this.currentNode = current;
        this.allNodes.add(current);
        if (current.equals(tx, ty, tz)) {
            return true;
        } else if (!goalWalkable) {
            // hack to get around unpathable target positions
            if (current.getDistanceFrom(tx, ty, tz) <= 2.d) {
                return true;
            }
        }
        if (shouldTerminateEarly(current)) {
            return true;
        }
        current.closed = true;
        this.findNeighbors(current);
        boolean isDoor = world.isDoor(current.getPos());
        boolean isPDoor = current.parentNode != null && world.isDoor(current.parentNode.getPos());
        for (Node n : this.searchNodes) {
            boolean isNDoor = world.isDoor(n.getPos());
            float tent = current.g + current.getDistanceFrom(n);
            if (n.closed && tent > n.g) {
                // the new path from the current node to n (an already examined node) is longer than n's current path, disregard
                continue;
            }
            if (!qNodes.contains(n) || tent < n.g) {
                // we haven't seen n before, or the path through current to n is shorter than n's best known path:
                // update n's stats to the path through current -> n.
                // This is where it deviates from A*: check whether n can see the parent of current, and if so
                // calculate the path to n as if it went through that parent, skipping current completely.
                if (!isPDoor && !isDoor && !isNDoor && canSeeParent(n, current.parentNode)) {
                    // don't skip doors
                    n.parentNode = current.parentNode;
                    n.g = n.parentNode.g + n.getDistanceFrom(n.parentNode);
                    n.f = n.g + n.getH(tx, ty, tz);
                } else {
                    // else if we cannot skip nodes, link n to current and calculate the path length
                    n.parentNode = current;
                    n.g = tent;
                    n.f = n.g + n.getH(tx, ty, tz);
                }
                if (!qNodes.contains(n)) {
                    // if we're not already going to examine n, put it in line to be examined
                    qNodes.offer(n);
                }
                n.closed = false;
            }
        }
        return false;
    }

    private boolean shouldTerminateEarly(Node current) {
        if (runTime > maxRunTime) {
            return true;
        }
        if (this.searchIteration > this.maxSearchIterations) {
            return true;
        }
        float dist = current.getDistanceFrom(tx, ty, tz);
        float len = current.getPathLength();
        if (dist < bestPathDist) {
            this.bestEndNode = current;
            this.bestPathDist = dist;
            this.bestPathLength = len;
        }
        return len > maxRange;
    }

    private boolean canSeeParent(Node n, @Nullable Node p) {
        if (p == null || n == null) {
            return false;
        }
        return PathUtils.canPathStraightToTargetLevel(world, n.x, n.y, n.z, p.x, p.y, p.z);
    }

    private void findNeighbors(Node n) {
        PathWorldAccess world = this.world;
        this.searchNodes.clear();
        tryAddSearchNode(n.x - 1, n.y, n.z, n);
        tryAddSearchNode(n.x + 1, n.y, n.z, n);
        tryAddSearchNode(n.x, n.y, n.z - 1, n);
        tryAddSearchNode(n.x, n.y, n.z + 1, n);
        // diagonals: check to make sure the path on the crossing blocks is clear
        if (world.isWalkable(n.x, n.y, n.z + 1) && world.isWalkable(n.x - 1, n.y, n.z)) {
            tryAddSearchNode(n.x - 1, n.y, n.z + 1, n);
        }
        if (world.isWalkable(n.x, n.y, n.z + 1) && world.isWalkable(n.x + 1, n.y, n.z)) {
            tryAddSearchNode(n.x + 1, n.y, n.z + 1, n);
        }
        if (world.isWalkable(n.x + 1, n.y, n.z) && world.isWalkable(n.x, n.y, n.z - 1)) {
            tryAddSearchNode(n.x + 1, n.y, n.z - 1, n);
        }
        if (world.isWalkable(n.x - 1, n.y, n.z) && world.isWalkable(n.x, n.y, n.z - 1)) {
            tryAddSearchNode(n.x - 1, n.y, n.z - 1, n);
        }
        // up/down (in case of a ladder or water)
        tryAddSearchNode(n.x, n.y + 1, n.z, n);
        tryAddSearchNode(n.x, n.y - 1, n.z, n);
        // and the NSEW y+1 and y-1 jumpable blocks (not diagonal jumps)
        tryAddSearchNode(n.x - 1, n.y + 1, n.z, n);
        tryAddSearchNode(n.x + 1, n.y + 1, n.z, n);
        tryAddSearchNode(n.x, n.y + 1, n.z - 1, n);
        tryAddSearchNode(n.x, n.y + 1, n.z + 1, n);
        tryAddSearchNode(n.x - 1, n.y - 1, n.z, n);
        tryAddSearchNode(n.x + 1, n.y - 1, n.z, n);
        tryAddSearchNode(n.x, n.y - 1, n.z - 1, n);
        tryAddSearchNode(n.x, n.y - 1, n.z + 1, n);
        // add the NSEW y-2 jumpable-down blocks
        if (world.canDrop) {
            tryAddSearchNode(n.x - 1, n.y - 2, n.z, n);
            tryAddSearchNode(n.x + 1, n.y - 2, n.z, n);
            tryAddSearchNode(n.x, n.y - 2, n.z - 1, n);
            tryAddSearchNode(n.x, n.y - 2, n.z + 1, n);
        }
    }

    private void tryAddSearchNode(int x, int y, int z, Node p) {
        PathWorldAccess world = this.world;
        if (x < minx || x > maxx || y < miny || y > maxy || z < minz || z > maxz) {
            return;
        }
        if (world.isWalkable(x, y, z)) {
            if (p != null && p.y != y && (p.z != z || p.x != x)) {
                // moving from a different height, but not directly up or down
                if (p.y > y) {
                    // moving down from the parent, check y -> y + 2
                    if (!world.checkBlockBounds(x, y + 2, z)) {
                        return;
                    }
                } else if (p.y < y) {
                    // moving up from the parent, check parent.y -> parent.y + 2
                    if (world.isPartialBlock(p.getPos().below())) {
                        // make sure it is not going to be too far to jump up
                        return;
                    }
                    if (!world.checkBlockBounds(p.x, p.y + 2, p.z)) {
                        return;
                    }
                }
            }
            searchNodes.add(getOrMakeNode(x, y, z, p));
        }
    }

    private Node getOrMakeNode(int x, int y, int z, @Nullable Node p) {
        for (Node c : this.allNodes) {
            if (c.equals(x, y, z)) {
                return c;
            }
        }
        Node n = new Node(x, y, z);
        if (p != null) {
            n.travelCost = world.getTravelCost(new BlockPos(x, y, z));
            n.parentNode = p;
            n.g = p.g + n.getDistanceFrom(p) + n.travelCost;
            n.f = n.g + n.getDistanceFrom(tx, ty, tz);
        }
        allNodes.add(n);
        return n;
    }
}
