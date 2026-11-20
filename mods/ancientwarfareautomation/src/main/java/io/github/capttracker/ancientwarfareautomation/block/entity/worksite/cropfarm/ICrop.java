/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/cropfarm/ICrop.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * How the crop farm treats one kind of crop. The handlers are kept in the {@code CropFarmRegistry}, which asks them
 * in order (most recently registered first) whether they {@link #matches(BlockState) match} a block.
 */
public interface ICrop {
    /** The positions to harvest for the block found at {@code pos} (none if the crop is not ready). */
    List<BlockPos> getPositionsToHarvest(Level level, BlockPos pos, BlockState state);

    boolean canBeFertilized(BlockState state, Level level, BlockPos pos);

    /**
     * Harvests the crop at the position with the given fortune level.
     *
     * @param inventory where the drops go; nothing is harvested if they do not fit
     * @return true if the crop was harvested
     */
    boolean harvest(Level level, BlockState state, BlockPos pos, int fortune, IItemHandler inventory);

    boolean matches(BlockState state);

    /** Whether this handler is the one that knows the item (used to decide if the item can be planted). */
    default boolean matches(ItemStack stack) {
        return false;
    }

    default boolean isPlantable(ItemStack stack) {
        return false;
    }
}
