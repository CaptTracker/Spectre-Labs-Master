/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/warehouse2/TileWarehouseCraftingStation.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.warehouse;

import io.github.capttracker.ancientwarfare.api.IBlockBreakHandler;
import io.github.capttracker.ancientwarfare.api.IInteractableTile;
import io.github.capttracker.ancientwarfare.block.entity.UpdatableBlockEntity;
import io.github.capttracker.ancientwarfare.crafting.CraftingRecipeMemory;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The warehouse crafting station: a crafting table that takes its ingredients from the warehouse whose control block
 * is directly below it ({@link #getWarehouse()}, looked up on every access; the station is not a controlled block).
 * It only holds the recipe memory (research book slot, 3x3 recipe grid, chosen recipe); the crafting itself is the
 * menu's job (GUI slice), through {@link WarehouseBlockEntity#getItemHandler()}.
 *
 * <p>The memory is saved and also sent to clients in the update tag (as in the original), which needs the registry
 * access, so the tag hooks with the registry parameter are overridden here instead of {@code writeUpdateNBT}.
 */
public class WarehouseCraftingStationBlockEntity extends UpdatableBlockEntity implements IInteractableTile, IBlockBreakHandler {

    public final CraftingRecipeMemory craftingRecipeMemory = new CraftingRecipeMemory(this::setChanged);

    public WarehouseCraftingStationBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.WAREHOUSE_CRAFTING_BE.get(), pos, state);
    }

    /** The control block below this block, or null if there is none. */
    @Nullable
    public final WarehouseBlockEntity getWarehouse() {
        if (level == null || worldPosition.getY() <= level.getMinBuildHeight() + 1) {//could not possibly be a warehouse below...
            return null;
        }
        return level.getBlockEntity(worldPosition.below()) instanceof WarehouseBlockEntity warehouse ? warehouse : null;
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide) {
            WarehouseMenus.openCraftingStation(player, this);
        }
        return true;
    }

    @Override
    public void onBlockBroken(BlockState state) {
        if (level != null) {
            craftingRecipeMemory.dropInventory(level, worldPosition);
        }
    }

    // ************************************** NBT AND PACKET DATA METHODS ***************************************//

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        craftingRecipeMemory.load(tag, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        craftingRecipeMemory.save(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        craftingRecipeMemory.save(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        craftingRecipeMemory.load(tag, registries);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        super.onDataPacket(net, packet, registries);
        craftingRecipeMemory.load(packet.getTag(), registries);
    }
}
