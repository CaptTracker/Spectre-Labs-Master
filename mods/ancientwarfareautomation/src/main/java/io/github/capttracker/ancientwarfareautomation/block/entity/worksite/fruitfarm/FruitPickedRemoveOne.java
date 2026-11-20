/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/fruitfarm/FruitPickedRemoveOne.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.fruitfarm;

import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfare.util.parsing.BlockStateMatcher;
import io.github.capttracker.ancientwarfare.util.parsing.PropertyState;
import io.github.capttracker.ancientwarfare.util.parsing.PropertyStateMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

/** A {@link FruitPicked} that keeps one item of the first drop (the seed) back, which is how the fruit regrows. */
public class FruitPickedRemoveOne extends FruitPicked {
    public FruitPickedRemoveOne(BlockStateMatcher stateMatcher, PropertyStateMatcher ripeStateMatcher, PropertyState<?, ?> newState) {
        super(stateMatcher, ripeStateMatcher, newState);
    }

    @Override
    protected void putInInventory(Level level, BlockPos pos, IItemHandler inventory, NonNullList<ItemStack> drops) {
        // the list is never empty here (pick refuses empty drops); the first drop is copied because it may be used up by the removal
        ItemStack first = drops.get(0).copy();
        InventoryTools.removeItem(drops, s -> InventoryTools.doItemStacksMatchRelaxed(s, first), 1);
        InventoryTools.insertOrDropItems(inventory, drops, level, pos);
    }
}
