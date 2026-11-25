/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/pathing/Node.
 */
package io.github.capttracker.ancientwarfarevehicle.pathing;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/** A node of the path finders: a block position with its A* costs and its parent. */
public class Node implements Comparable<Node> {
    public float goalLenght;
    public float travelCost = 10;
    @Nullable
    public Node parentNode = null;
    public float g = Float.POSITIVE_INFINITY;
    public float f;
    public int x;
    public int y;
    public int z;
    public boolean obstacle = false;
    public boolean closed = false;

    public Node(BlockPos pos) {
        this(pos.getX(), pos.getY(), pos.getZ());
    }

    public Node(int bX, int bY, int bZ) {
        this.x = bX;
        this.y = bY;
        this.z = bZ;
    }

    public Node(int x, int y, int z, Node parent, Node goal, float g) {
        this(x, y, z);
        this.parentNode = parent;
        this.g = g;
        this.f = this.getH(goal) + this.g;
    }

    public Node reassign(BlockPos pos) {
        return reassign(pos.getX(), pos.getY(), pos.getZ());
    }

    public Node reassign(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    /** Calculates the travel cost of this node and sets it to an obstacle if it is completely unpathable (solid). */
    public void calcTraveCost(PathWorldAccess world, Node parentNode) {
        if (world == null) {
            return;
        }
        this.obstacle = false;
        this.travelCost = 10;
        this.obstacle = !world.isWalkable(x, y, z, parentNode);
    }

    protected float getH(Node b) {
        return getDistanceFrom(b) * 10 + travelCost;
    }

    protected float getH(int tx, int ty, int tz) {
        return getDistanceFrom(tx, ty, tz) * 10 + travelCost;
    }

    @Override
    public int compareTo(Node other) {
        // lower cost = smaller natural value
        return Float.compare(f, other.f);
    }

    @Override
    public boolean equals(Object checkagainst) {
        if (checkagainst instanceof Node check) {
            return check.x == x && check.y == y && check.z == z;
        }
        return false;
    }

    public boolean equals(int x, int y, int z) {
        return this.x == x && this.y == y && this.z == z;
    }

    public float getDistanceFrom(@Nullable Node node) {
        if (node == null) {
            return 0;
        }
        float dx = this.x - node.x;
        float dy = this.y - node.y;
        float dz = this.z - node.z;
        return Mth.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public float getDistanceFrom(int x, int y, int z) {
        float x1 = this.x - x;
        float y1 = this.y - y;
        float z1 = this.z - z;
        return Mth.sqrt(x1 * x1 + y1 * y1 + z1 * z1);
    }

    public float getPathLength() {
        float len = 0;
        if (this.parentNode != null) {
            len += this.getDistanceFrom(parentNode);
            len += this.parentNode.getPathLength();
        }
        return len;
    }

    public BlockPos getPos() {
        return new BlockPos(x, y, z);
    }

    @Override
    public int hashCode() {
        return (x << 16) ^ z ^ (y << 24);
    }

    @Override
    public String toString() {
        return "Node: " + x + "," + y + "," + z + " TC: " + travelCost + " F: " + f;
    }
}
