/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building zones.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * A buildable plot: a rectangle in X and Z inside the land of a settlement, marked with the plot marker. Builders only build inside plots. The
 * rectangle includes both corner columns ({@code min} and {@code max} are inclusive); the height is not limited. {@link #y} is the ground height at
 * the first corner when the plot was marked (shown to the player only).
 */
public final class BuildingPlot {
    private final UUID id;
    private final int number;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final int y;

    public BuildingPlot(UUID id, int number, int minX, int minZ, int maxX, int maxZ, int y) {
        this.id = id;
        this.number = number;
        this.minX = Math.min(minX, maxX);
        this.maxX = Math.max(minX, maxX);
        this.minZ = Math.min(minZ, maxZ);
        this.maxZ = Math.max(minZ, maxZ);
        this.y = y;
    }

    public UUID getId() {
        return id;
    }

    /** The number shown to the player ("Plot 3"); never reused inside a settlement. */
    public int getNumber() {
        return number;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return maxX - minX + 1;
    }

    public int getDepth() {
        return maxZ - minZ + 1;
    }

    public double centerX() {
        return (minX + maxX + 1) / 2.0D;
    }

    public double centerZ() {
        return (minZ + maxZ + 1) / 2.0D;
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** Whether a rectangle (inclusive corners) lies completely inside the plot. */
    public boolean containsRect(int x1, int z1, int x2, int z2) {
        return x1 >= minX && x2 <= maxX && z1 >= minZ && z2 <= maxZ;
    }

    public boolean intersects(int x1, int z1, int x2, int z2) {
        return x1 <= maxX && x2 >= minX && z1 <= maxZ && z2 >= minZ;
    }

    public boolean intersects(BuildingPlot other) {
        return intersects(other.minX, other.minZ, other.maxX, other.maxZ);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putInt("n", number);
        tag.putInt("x1", minX);
        tag.putInt("z1", minZ);
        tag.putInt("x2", maxX);
        tag.putInt("z2", maxZ);
        tag.putInt("y", y);
        return tag;
    }

    public static BuildingPlot load(CompoundTag tag) {
        if (!tag.hasUUID("id")) {
            return null;
        }
        return new BuildingPlot(tag.getUUID("id"), tag.getInt("n"), tag.getInt("x1"), tag.getInt("z1"), tag.getInt("x2"), tag.getInt("z2"), tag.getInt("y"));
    }
}
