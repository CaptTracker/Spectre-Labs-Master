/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/treefarm/IBlockExtraDrop.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Something a block gives when the tree farm cuts it, besides what its loot table drops. */
public interface IBlockExtraDrop {
    boolean matches(BlockState state);

    NonNullList<ItemStack> getDrops(BlockGetter level, BlockPos pos, BlockState state, int fortune);
}
