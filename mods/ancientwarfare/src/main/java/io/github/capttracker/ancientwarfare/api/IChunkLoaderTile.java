/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/interfaces/IChunkLoaderTile.
 */
package io.github.capttracker.ancientwarfare.api;

import net.minecraft.server.level.ServerLevel;

/**
 * A block entity that keeps chunks loaded. 1.12.2 handed such a tile a {@code ForgeChunkManager.Ticket} through
 * {@code setTicket}, from a loading callback after a world load. 1.21.1 has no tickets handed around: the tile
 * uses a NeoForge {@code TicketController} (registered by the Automation slice in
 * {@code RegisterTicketControllersEvent}), whose forced chunks are saved with the level.
 */
public interface IChunkLoaderTile {

    /**
     * Force the chunks this tile needs (for example with {@code controller.forceChunk(level, pos, cx, cz, true, true)}).
     * Called when the tile loads and whenever the set of chunks should change. Must be safe to call repeatedly.
     */
    void requestChunkTickets(ServerLevel level);

    /** Release every chunk this tile forced (1.12.2: {@code releaseTicket}). Called when the tile is removed. */
    void releaseChunkTickets(ServerLevel level);
}
