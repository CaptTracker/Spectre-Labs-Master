/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockChunkLoaderDeluxe.
 */
package io.github.capttracker.ancientwarfareautomation.block.chunkloader;

import io.github.capttracker.ancientwarfareautomation.chunkloader.ChunkLoaderDeluxeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** The deluxe chunk loader block: the same as the simple one with another block entity (right-click opens the chunk selection screen). */
public class ChunkLoaderDeluxeBlock extends ChunkLoaderSimpleBlock {
    public ChunkLoaderDeluxeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChunkLoaderDeluxeBlockEntity(pos, state);
    }
}
