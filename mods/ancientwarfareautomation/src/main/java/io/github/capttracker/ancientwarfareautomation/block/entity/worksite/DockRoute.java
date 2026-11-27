/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the water routes of the fishing dock's boat.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipRoutePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Finds the waypoints a ship steers through over water round the dock: a breadth first search over the cells (1 x 1 blocks) of the water, then the path is cut
 * short wherever a straight line between two of its cells is clear. A cell is water at least 2 deep (the ships' own rule, see {@code ShipHelper.MIN_DEPTH}) with
 * nothing solid in the two blocks above it. A cell is clear if the 3 x 3 cells round it are water too, so a hull fits; the cells near the start and the goal are
 * allowed to be next to the shore. Chunks that are not loaded count as land.
 *
 * <p>Rework phase 11: the search itself moved to the Vehicles module ({@link ShipRoutePlanner}) so that the sea trade can use it for wider hulls and longer
 * routes; the fishing boat's routes are unchanged (clearance 1, the dock's range plus a margin).
 */
final class DockRoute {
    private static final int MARGIN = ShipRoutePlanner.MARGIN;

    private DockRoute() {}

    /** The y of the top water block of the column, scanning down from {@code refY + 3}, or {@link Integer#MIN_VALUE} if there is none. */
    static int surfaceY(Level level, int x, int z, int refY) {
        return ShipRoutePlanner.surfaceY(level, x, z, refY);
    }

    /** True if a ship floats here: loaded, water 2 or more deep, and open above the surface. */
    static boolean deepWater(Level level, int x, int z, int refY) {
        return ShipRoutePlanner.deepWater(level, x, z, refY);
    }

    /** True if the cell and the 8 cells round it are deep water. */
    static boolean clear(Level level, int x, int z, int refY) {
        return ShipRoutePlanner.clear(level, x, z, refY);
    }

    /**
     * The waypoints from {@code from} to the target, the last one in the target's cell or a cell next to it; null if there is no way. {@code center} and
     * {@code range} bound the search (the dock and its range).
     */
    @Nullable
    static List<Vec3> plan(Level level, Vec3 from, BlockPos target, BlockPos center, int range, int refY) {
        return ShipRoutePlanner.plan(level, from, target, center, range + MARGIN, refY, 1);
    }
}
