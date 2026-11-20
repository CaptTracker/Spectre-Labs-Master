/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/chunkloader/AWChunkLoader (a ForgeChunkManager LoadingCallback).
 */
package io.github.capttracker.ancientwarfareautomation.chunkloader;

import io.github.capttracker.ancientwarfare.api.IChunkLoaderTile;
import io.github.capttracker.ancientwarfareautomation.AncientWarfareAutomation;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

/**
 * The ticket controller of the chunk loader blocks and of the worksites with a chunk loader upgrade.
 *
 * <p>1.12.2 requested a {@code ForgeChunkManager.Ticket} per tile, wrote the tile position into the ticket and
 * handed the ticket back to the tile in a loading callback after a restart. A NeoForge {@link TicketController}
 * has no ticket objects: a block position "owns" a set of forced chunks, NeoForge saves them with the level and
 * re-applies them on load. What is left of the callback is {@link #validateTickets}, which drops the tickets of
 * positions where no chunk loading block entity is left (the block was removed while the chunk was not loaded).
 *
 * <p>Wiring: {@code modBus.addListener(AWChunkLoader::register)} (done by {@code AwAutomationBase.registerEvents}).
 */
public final class AWChunkLoader {
    /** Controllers must be registered in {@link RegisterTicketControllersEvent}, or forcing a chunk throws. */
    public static final TicketController CONTROLLER =
            new TicketController(Ids.of(AncientWarfareAutomation.MOD_ID, "chunk_loader"), AWChunkLoader::validateTickets);

    private AWChunkLoader() {}

    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    /** Called when a level loads: removes the tickets owned by positions that do not hold a chunk loading block entity any more. */
    private static void validateTickets(ServerLevel level, TicketHelper ticketHelper) {
        // The map does not change when tickets are removed, so removing while iterating is fine.
        for (BlockPos owner : ticketHelper.getBlockTickets().keySet()) {
            if (!(level.getBlockEntity(owner) instanceof IChunkLoaderTile)) {
                ticketHelper.removeAllTickets(owner);
            }
        }
    }

    /** Forces (and ticks) the chunk for the block position. @return true if the chunk was not forced by that position before */
    public static boolean forceChunk(ServerLevel level, BlockPos owner, ChunkPos chunk) {
        return CONTROLLER.forceChunk(level, owner, chunk.x, chunk.z, true, true);
    }

    /** Stops forcing the chunk for the block position. @return true if the position had forced it */
    public static boolean unforceChunk(ServerLevel level, BlockPos owner, ChunkPos chunk) {
        return CONTROLLER.forceChunk(level, owner, chunk.x, chunk.z, false, true);
    }
}
