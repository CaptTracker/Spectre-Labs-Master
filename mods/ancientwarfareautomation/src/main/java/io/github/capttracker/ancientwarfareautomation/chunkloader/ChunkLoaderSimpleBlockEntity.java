/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/TileChunkLoaderSimple.
 */
package io.github.capttracker.ancientwarfareautomation.chunkloader;

import io.github.capttracker.ancientwarfare.api.IBlockBreakHandler;
import io.github.capttracker.ancientwarfare.api.IChunkLoaderTile;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The simple chunk loader: keeps the 3x3 chunks around its own chunk loaded and ticking.
 *
 * <p>The block ({@code ChunkLoaderSimpleBlock}) must call {@link #setupInitialTicket()} from {@code setPlacedBy}
 * (server side; 1.12.2 {@code onBlockPlacedBy}), and the tickets must be released when the block is removed: extend
 * {@code BaseBlock}, whose {@code onRemove} calls {@link #onBlockBroken}, or call {@link #releaseTicket()} from
 * the block (1.12.2 {@code breakBlock}). The tickets are not released when the block entity is merely unloaded
 * ({@code setRemoved} is also called on chunk unload in 1.21, unlike {@code invalidate}); the chunks stay
 * forced like the 1.12.2 ticket did.
 */
public class ChunkLoaderSimpleBlockEntity extends BlockEntity implements IChunkLoaderTile, IBlockBreakHandler {
    protected final ChunkTicketSet tickets = new ChunkTicketSet();

    public ChunkLoaderSimpleBlockEntity(BlockPos pos, BlockState state) {
        this(AwAutomationBase.CHUNK_LOADER_SIMPLE_BE.get(), pos, state);
    }

    protected ChunkLoaderSimpleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Forces the chunks of this loader. Server side, after the block was placed. */
    public void setupInitialTicket() {
        if (level instanceof ServerLevel serverLevel) {
            requestChunkTickets(serverLevel);
        }
    }

    @Override
    public void requestChunkTickets(ServerLevel level) {
        forceTicketChunks(level);
    }

    @Override
    public void releaseChunkTickets(ServerLevel level) {
        tickets.releaseAll(level, worldPosition);
        setChanged();
    }

    /** Stops forcing the chunks (1.12.2 {@code releaseTicket}). Does nothing on the client. */
    public void releaseTicket() {
        if (level instanceof ServerLevel serverLevel) {
            releaseChunkTickets(serverLevel);
        }
    }

    @Override
    public void onBlockBroken(BlockState state) {
        releaseTicket();
    }

    protected void forceTicketChunks(ServerLevel level) {
        int cx = worldPosition.getX() >> 4;
        int cz = worldPosition.getZ() >> 4;
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                tickets.force(level, worldPosition, new ChunkPos(x, z));
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tickets.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tickets.load(tag);
    }
}
