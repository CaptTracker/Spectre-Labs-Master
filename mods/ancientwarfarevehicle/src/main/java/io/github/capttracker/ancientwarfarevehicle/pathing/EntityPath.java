/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/pathing/EntityPath.
 */
package io.github.capttracker.ancientwarfarevehicle.pathing;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/** The path an entity follows: a list of nodes that it claims one by one. */
public class EntityPath {
    private final LinkedList<Node> path = new LinkedList<>();

    public void setPath(List<Node> pathNodes) {
        this.clearPath();
        this.path.addAll(pathNodes);
    }

    public void clearPath() {
        this.path.clear();
    }

    /**
     * Adds nodes onto a path towards a new or updated target: checks the new path against the old one and
     * removes the nodes that are no longer needed.
     */
    public void addPath(PathWorldAccess world, List<Node> pathNodes) {
        // check the current path from the (current) start, try to 'see' the start node of the new path, and if
        // so, remove the rest of the current path
        if (pathNodes.isEmpty()) {
            return;
        }
        Node start = pathNodes.get(0);
        Iterator<Node> it = this.path.iterator();
        boolean couldSee = false;
        while (it.hasNext()) {
            Node n = it.next();
            if (!couldSee) {
                if (PathUtils.canPathStraightToTarget(world, n.x, n.y, n.z, start.getPos())) {
                    couldSee = true;
                }
            } else {
                it.remove();
            }
        }
        this.path.addAll(pathNodes);
    }

    public boolean containsPoint(int x, int y, int z) {
        for (Node n : this.path) {
            if (n.equals(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public Node getEndNode() {
        return this.path.peekLast();
    }

    /** The first node (does not remove it). */
    @Nullable
    public Node getFirstNode() {
        return this.path.peekFirst();
    }

    /** Returns and removes the first node. */
    @Nullable
    public Node claimNode() {
        return this.path.poll();
    }

    public int getActivePathSize() {
        return this.path.size();
    }

    public float getActivePathLength() {
        if (this.path.isEmpty()) {
            return 0;
        }
        return this.path.get(path.size() - 1).getPathLength();
    }

    public List<Node> getActivePath() {
        return path;
    }
}
