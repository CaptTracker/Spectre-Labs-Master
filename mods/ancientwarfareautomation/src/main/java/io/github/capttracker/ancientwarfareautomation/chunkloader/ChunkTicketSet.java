/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: replaces the ForgeChunkManager.Ticket field that TileChunkLoaderSimple and TileWorksiteBounded held.
 */
package io.github.capttracker.ancientwarfareautomation.chunkloader;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The chunks one block entity has forced through {@link AWChunkLoader#CONTROLLER}, so it can stop forcing exactly
 * those chunks later (the 1.12.2 ticket object knew its chunk list; a NeoForge ticket controller does not tell).
 * It is saved in the block entity's NBT, which also keeps it right across a restart.
 *
 * <p>Server side only. The chunk set is the block entity's own; several block entities may force the same chunk,
 * NeoForge keeps one ticket per owner position.
 */
public final class ChunkTicketSet {
    private static final String TAG = "forcedChunks";

    private final LongSet forced = new LongLinkedOpenHashSet();

    /** Forces the chunk for the owner position; safe to call again for a chunk that is already forced. */
    public void force(ServerLevel level, BlockPos owner, ChunkPos chunk) {
        // A ticket that survived a restart is already there and the call then reports false; it is ours all the same.
        AWChunkLoader.forceChunk(level, owner, chunk);
        forced.add(chunk.toLong());
    }

    public void unforce(ServerLevel level, BlockPos owner, ChunkPos chunk) {
        AWChunkLoader.unforceChunk(level, owner, chunk);
        forced.remove(chunk.toLong());
    }

    /** Stops forcing every chunk this set forced. */
    public void releaseAll(ServerLevel level, BlockPos owner) {
        for (long packed : new LongArrayList(forced)) {
            AWChunkLoader.unforceChunk(level, owner, new ChunkPos(packed));
        }
        forced.clear();
    }

    public boolean isEmpty() {
        return forced.isEmpty();
    }

    public boolean contains(ChunkPos chunk) {
        return forced.contains(chunk.toLong());
    }

    public Set<ChunkPos> getForcedChunks() {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        forced.forEach((long packed) -> chunks.add(new ChunkPos(packed)));
        return chunks;
    }

    public void save(CompoundTag tag) {
        if (!forced.isEmpty()) {
            tag.putLongArray(TAG, forced.toLongArray());
        }
    }

    public void load(CompoundTag tag) {
        forced.clear();
        for (long packed : tag.getLongArray(TAG)) {
            forced.add(packed);
        }
    }
}
