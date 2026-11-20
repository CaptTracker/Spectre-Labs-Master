/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/MathUtils.
 */
package io.github.capttracker.ancientwarfare.util;

import com.google.common.collect.AbstractIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

/**
 * Small math helpers. Packing a position into a long is {@code BlockPos#asLong} / {@code BlockPos.of(long)}
 * now, and {@link #toLong}/{@link #fromLong} delegate to them (the bit layout differs from 1.12.2, which only
 * matters for data saved by the original, and none carries over). The box iterator is
 * {@code BlockPos.betweenClosed} for positions. Kept only what the other slices still call by name.
 */
public final class MathUtils {
    private MathUtils() {}

    public static boolean epsilonEquals(float valueA, float valueB) {
        return Math.abs(valueB - valueA) < 1.0E-5F;
    }

    /** All integer vectors in the box (inclusive), X fastest, then Y, then Z; the corners can be given in any order. */
    public static Iterable<Vec3i> getAllVecsInBox(Vec3i from, Vec3i to) {
        return getAllVecsInBox(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()),
                Math.max(from.getX(), to.getX()), Math.max(from.getY(), to.getY()), Math.max(from.getZ(), to.getZ()));
    }

    private static Iterable<Vec3i> getAllVecsInBox(final int x1, final int y1, final int z1, final int x2, final int y2, final int z2) {
        return () -> new AbstractIterator<Vec3i>() {
            private boolean first = true;
            private int lastPosX;
            private int lastPosY;
            private int lastPosZ;

            @Override
            protected Vec3i computeNext() {
                if (this.first) {
                    this.first = false;
                    this.lastPosX = x1;
                    this.lastPosY = y1;
                    this.lastPosZ = z1;
                    return new BlockPos(x1, y1, z1);
                } else if (this.lastPosX == x2 && this.lastPosY == y2 && this.lastPosZ == z2) {
                    return this.endOfData();
                } else {
                    if (this.lastPosX < x2) {
                        ++this.lastPosX;
                    } else if (this.lastPosY < y2) {
                        this.lastPosX = x1;
                        ++this.lastPosY;
                    } else if (this.lastPosZ < z2) {
                        this.lastPosX = x1;
                        this.lastPosY = y1;
                        ++this.lastPosZ;
                    }

                    return new Vec3i(this.lastPosX, this.lastPosY, this.lastPosZ);
                }
            }
        };
    }

    /** Vanilla's packed long layout: {@code BlockPos#asLong}. */
    public static Vec3i fromLong(long serialized) {
        return BlockPos.of(serialized);
    }

    public static long toLong(Vec3i vector) {
        return BlockPos.asLong(vector.getX(), vector.getY(), vector.getZ());
    }

    public static int[] toIntArray(short[] array) {
        int[] ret = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            ret[i] = array[i];
        }
        return ret;
    }

    public static short[] toShortArray(int[] array) {
        short[] ret = new short[array.length];
        for (int i = 0; i < array.length; i++) {
            ret[i] = (short) array[i];
        }
        return ret;
    }
}
