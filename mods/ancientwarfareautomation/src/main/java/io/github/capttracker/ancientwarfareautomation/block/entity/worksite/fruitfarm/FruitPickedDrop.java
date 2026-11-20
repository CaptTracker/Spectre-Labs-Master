/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/fruitfarm/FruitPickedDrop.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.fruitfarm;

import io.github.capttracker.ancientwarfare.util.parsing.BlockStateMatcher;
import io.github.capttracker.ancientwarfare.util.parsing.PropertyState;
import io.github.capttracker.ancientwarfare.util.parsing.PropertyStateMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** A {@link FruitPicked} that always gives the fixed stack of the data file instead of the block's own drops. */
public class FruitPickedDrop extends FruitPicked {
    private final ItemStack drop;

    public FruitPickedDrop(BlockStateMatcher stateMatcher, PropertyStateMatcher ripeStateMatcher, PropertyState<?, ?> newState, ItemStack drop) {
        super(stateMatcher, ripeStateMatcher, newState);
        this.drop = drop;
    }

    @Override
    protected NonNullList<ItemStack> getDrops(ServerLevel level, BlockState state, BlockPos pos, int fortune) {
        return NonNullList.of(ItemStack.EMPTY, drop.copy());
    }
}
