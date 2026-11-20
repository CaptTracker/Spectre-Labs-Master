/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 1): settlements.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.ancientwarfare.block.entity.UpdatableBlockEntity;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcSettlements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** Holds the banner colour of a marker (the colour of the owner's faction when it was placed) and sends it to the clients. */
public class SettlementMarkerBlockEntity extends UpdatableBlockEntity {
    private int color = SettlementEvents.DEFAULT_COLOR;

    public SettlementMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(AwNpcSettlements.SETTLEMENT_MARKER_BE.get(), pos, state);
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
        setChanged();
        syncToClients();
    }

    @Override
    protected void writeUpdateNBT(CompoundTag tag) {
        tag.putInt("color", color);
    }

    @Override
    protected void handleUpdateNBT(CompoundTag tag) {
        int newColor = tag.getInt("color");
        if (newColor != color) {
            color = newColor;
            if (level != null && level.isClientSide) {
                BlockState state = getBlockState();
                level.sendBlockUpdated(worldPosition, state, state, 0);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("color")) {
            color = tag.getInt("color");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("color", color);
    }
}
