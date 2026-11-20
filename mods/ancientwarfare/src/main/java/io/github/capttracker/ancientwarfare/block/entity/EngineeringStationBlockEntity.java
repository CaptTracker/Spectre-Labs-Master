/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/tile/TileEngineeringStation and core/tile/CraftingRecipeMemory.
 */
package io.github.capttracker.ancientwarfare.block.entity;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.crafting.CraftingRecipeMemory;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Engineering bench storage: the recipe memory (research book slot, 3x3 recipe grid that stays filled as a template,
 * chosen recipe) and 18 storage slots that crafting draws its ingredients from.
 *
 * <p>The facing that 1.12.2 stored here as well as in metadata now lives only in the block state.
 */
public class EngineeringStationBlockEntity extends BlockEntity {
    public static final int GRID_SIZE = 9;
    public static final int STORAGE_SIZE = 18;

    public final CraftingRecipeMemory memory = new CraftingRecipeMemory(this::setChanged);
    /** 1.12.2: {@code extraSlots}. */
    public final ItemStackHandler storage = new ItemStackHandler(STORAGE_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public EngineeringStationBlockEntity(BlockPos pos, BlockState state) {
        super(AncientWarfareCore.ENGINEERING_STATION_BE.get(), pos, state);
    }

    /** Called when the block is broken (1.12.2: onBlockBreak). */
    public void dropContents(Level level, BlockPos pos) {
        memory.dropInventory(level, pos);
        InventoryTools.dropItemsInWorld(level, storage, pos);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        memory.save(tag, registries);
        tag.put("extraInventory", storage.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        memory.load(tag, registries);
        storage.deserializeNBT(registries, tag.getCompound("extraInventory"));
    }
}
