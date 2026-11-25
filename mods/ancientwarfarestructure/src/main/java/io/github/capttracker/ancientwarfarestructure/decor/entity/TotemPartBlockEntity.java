/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/tile/TileTotemPart and TileMulti (the main-block link of the multi-block).
 */
package io.github.capttracker.ancientwarfarestructure.decor.entity;

import io.github.capttracker.ancientwarfare.api.IBlockBreakHandler;
import io.github.capttracker.ancientwarfare.block.entity.UpdatableBlockEntity;
import io.github.capttracker.ancientwarfarestructure.decor.TotemPartBlock;
import io.github.capttracker.ancientwarfarestructure.decor.TotemVariant;
import io.github.capttracker.ancientwarfarestructure.init.AwDecor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Block entity of a totem part (synced to clients through the update tag). It no longer stores the variant (that is a block-state property now); it keeps what
 * the multi-block needs:
 * <ul>
 *   <li>the position of the main block, for every extra part (the main block itself has none), so that breaking any
 *   part removes the whole totem;</li>
 *   <li>the variant of the main block, which is what any part drops and picks (1.12.2: {@code dropVariant}, which was
 *   re-derived from the main tile on load and is saved here instead, so it survives an unloaded main block).</li>
 * </ul>
 */
public class TotemPartBlockEntity extends UpdatableBlockEntity implements IBlockBreakHandler {
    private static final String MAIN_BLOCK_POS_TAG = "mainBlockPos";
    private static final String DROP_VARIANT_TAG = "dropVariant";

    @Nullable
    private BlockPos mainBlockPos;
    @Nullable
    private TotemVariant dropVariant;

    public TotemPartBlockEntity(BlockPos pos, BlockState state) {
        super(AwDecor.TOTEM_PART_BE.get(), pos, state);
    }

    /** Marks this block as an extra part of the totem whose main block is at {@code mainPos}. */
    public void setMain(BlockPos mainPos, TotemVariant mainVariant) {
        this.mainBlockPos = mainPos;
        this.dropVariant = mainVariant;
        setChanged();
        syncToClients();
    }

    /** Marks this block as the main block of a totem of the given variant. */
    public void setMainVariant(TotemVariant mainVariant) {
        this.mainBlockPos = null;
        this.dropVariant = mainVariant;
        setChanged();
        syncToClients();
    }

    @Nullable
    public BlockPos getMainBlockPos() {
        return mainBlockPos;
    }

    /** The variant of the totem's main block; falls back to this part's own variant when nothing was recorded. */
    public TotemVariant getDropVariant() {
        if (dropVariant != null) {
            return dropVariant;
        }
        return getBlockState().hasProperty(TotemPartBlock.VARIANT) ? getBlockState().getValue(TotemPartBlock.VARIANT) : TotemVariant.BASE;
    }

    private void writeData(CompoundTag tag) {
        if (mainBlockPos != null) {
            tag.putLong(MAIN_BLOCK_POS_TAG, mainBlockPos.asLong());
        }
        if (dropVariant != null) {
            tag.putByte(DROP_VARIANT_TAG, (byte) dropVariant.getId());
        }
    }

    private void readData(CompoundTag tag) {
        mainBlockPos = tag.contains(MAIN_BLOCK_POS_TAG) ? BlockPos.of(tag.getLong(MAIN_BLOCK_POS_TAG)) : null;
        dropVariant = tag.contains(DROP_VARIANT_TAG) ? TotemVariant.fromId(tag.getByte(DROP_VARIANT_TAG)) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeData(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readData(tag);
    }

    // The client needs the same two values (pick block gives the main block's variant); 1.12.2 synced mainBlockPos.
    @Override
    protected void writeUpdateNBT(CompoundTag tag) {
        writeData(tag);
    }

    @Override
    protected void handleUpdateNBT(CompoundTag tag) {
        readData(tag);
    }

    /**
     * Called when this block is removed (1.12.2: TileMulti.onBlockBroken). An extra part removes the main block, which
     * removes all of its extra parts; the main block removes its extra parts directly. Nothing drops here: the block
     * that the player broke drops the totem once (see {@code TotemPartBlock#getDrops}).
     */
    @Override
    public void onBlockBroken(BlockState state) {
        Level level = this.level;
        if (level == null || level.isClientSide) {
            return;
        }
        BlockPos pos = getBlockPos();
        if (mainBlockPos != null && !mainBlockPos.equals(pos)) {
            BlockState mainState = level.getBlockState(mainBlockPos);
            if (!mainState.isAir()) {
                if (level.getBlockEntity(mainBlockPos) instanceof TotemPartBlockEntity main) {
                    main.onBlockBroken(mainState);
                }
                level.setBlock(mainBlockPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            return;
        }
        if (state.getBlock() instanceof TotemPartBlock && state.hasProperty(TotemPartBlock.VARIANT)) {
            for (BlockPos extra : state.getValue(TotemPartBlock.VARIANT).additionalPositions(pos, state.getValue(TotemPartBlock.FACING))) {
                // Only totem blocks: the 1.12.2 code cleared the positions whatever was there now.
                if (level.getBlockState(extra).getBlock() instanceof TotemPartBlock) {
                    level.setBlock(extra, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }
}
