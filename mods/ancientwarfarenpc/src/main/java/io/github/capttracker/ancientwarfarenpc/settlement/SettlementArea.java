/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 1): settlements.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The land of a settlement, as pure geometry (the client uses it too, to draw the border).
 *
 * <p>With 3 or more markers the area is the polygon of the markers, taken in angle order around the hall and tested in XZ only (the full
 * height is inside). With fewer markers, or if the hall is not inside the polygon, it is a circle of the default radius around the hall.
 */
public final class SettlementArea {
    public static final int MIN_POLYGON_MARKERS = 3;

    private SettlementArea() {}

    /** The markers in angle order around the hall. */
    public static List<BlockPos> sortedByAngle(BlockPos hall, Collection<BlockPos> markers) {
        List<BlockPos> sorted = new ArrayList<>(markers);
        double cx = hall.getX() + 0.5D;
        double cz = hall.getZ() + 0.5D;
        sorted.sort(Comparator.comparingDouble(p -> Math.atan2(p.getZ() + 0.5D - cz, p.getX() + 0.5D - cx)));
        return sorted;
    }

    /** Ray casting point in polygon test in XZ. */
    public static boolean polygonContains(List<BlockPos> polygon, double x, double z) {
        boolean inside = false;
        int n = polygon.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon.get(i).getX() + 0.5D;
            double zi = polygon.get(i).getZ() + 0.5D;
            double xj = polygon.get(j).getX() + 0.5D;
            double zj = polygon.get(j).getZ() + 0.5D;
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** Whether the hall would be inside the polygon of these markers (always true below 3 markers, where the radius is used). */
    public static boolean hallInside(BlockPos hall, Collection<BlockPos> markers) {
        for (BlockPos marker : markers) {
            if (marker.getX() == hall.getX() && marker.getZ() == hall.getZ()) {
                return false; // a marker above or below the hall has no angle and would put the hall on the border
            }
        }
        if (markers.size() < MIN_POLYGON_MARKERS) {
            return true;
        }
        return polygonContains(sortedByAngle(hall, markers), hall.getX() + 0.5D, hall.getZ() + 0.5D);
    }

    /** The polygon in angle order if the markers make a valid area (3 or more, hall inside), else an empty list (the radius is used). */
    public static List<BlockPos> polygon(BlockPos hall, Collection<BlockPos> markers) {
        if (markers.size() < MIN_POLYGON_MARKERS) {
            return List.of();
        }
        List<BlockPos> sorted = sortedByAngle(hall, markers);
        return polygonContains(sorted, hall.getX() + 0.5D, hall.getZ() + 0.5D) ? sorted : List.of();
    }

    public static boolean contains(BlockPos hall, Collection<BlockPos> markers, int radius, double x, double z) {
        List<BlockPos> polygon = polygon(hall, markers);
        if (!polygon.isEmpty()) {
            return polygonContains(polygon, x, z);
        }
        double dx = x - (hall.getX() + 0.5D);
        double dz = z - (hall.getZ() + 0.5D);
        return dx * dx + dz * dz <= (double) radius * radius;
    }
}
