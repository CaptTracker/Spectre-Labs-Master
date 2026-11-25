/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/pathing/IPathableCallback.
 */
package io.github.capttracker.ancientwarfarevehicle.pathing;

import java.util.List;

public interface IPathableCallback {
    void onPathFound(List<Node> pathNodes);
}
