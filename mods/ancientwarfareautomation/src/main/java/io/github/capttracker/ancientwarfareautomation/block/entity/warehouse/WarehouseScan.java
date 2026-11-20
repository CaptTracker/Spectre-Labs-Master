/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/WorldTools.getTileEntitiesInArea, as used by the warehouse code
 * (automation/tile/warehouse2/TileControlled and TileWarehouseBase).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.warehouse;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Looks for block entities in a box without loading chunks. Core's {@code WorldTools.getTileEntitiesInArea} does what
 * the 1.12.2 one did and loads every chunk the box touches; the warehouse scans run from block entity ticks and
 * from {@code setRemoved}, where loading a chunk is a bad idea in 1.21.1, so only chunks that are already loaded
 * are looked at.
 */
final class WarehouseScan {
    private WarehouseScan() {}

    /** The block entities (not removed) inside the box, from the loaded chunks only. Inclusive bounds. */
    static List<BlockEntity> loadedTilesInArea(Level level, int x1, int y1, int z1, int x2, int y2, int z2) {
        List<BlockEntity> found = new ArrayList<>();
        for (int chunkX = x1 >> 4; chunkX <= (x2 >> 4); chunkX++) {
            for (int chunkZ = z1 >> 4; chunkZ <= (z2 >> 4); chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    if (!be.isRemoved() && isInArea(be, x1, y1, z1, x2, y2, z2)) {
                        found.add(be);
                    }
                }
            }
        }
        return found;
    }

    private static boolean isInArea(BlockEntity be, int x1, int y1, int z1, int x2, int y2, int z2) {
        int x = be.getBlockPos().getX();
        int y = be.getBlockPos().getY();
        int z = be.getBlockPos().getZ();
        return x >= x1 && y >= y1 && z >= z1 && x <= x2 && y <= y2 && z <= z2;
    }
}
