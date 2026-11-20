/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/treefarm/ChorusFlowerDrop.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** A chorus flower drops itself when the tree farm cuts it (the flower is the plantable of the chorus tree). */
public class ChorusFlowerDrop implements IBlockExtraDrop {
    @Override
    public boolean matches(BlockState state) {
        return state.is(Blocks.CHORUS_FLOWER);
    }

    @Override
    public NonNullList<ItemStack> getDrops(BlockGetter level, BlockPos pos, BlockState state, int fortune) {
        return NonNullList.of(ItemStack.EMPTY, new ItemStack(Items.CHORUS_FLOWER));
    }
}
