/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/fruitfarm/FruitPicked.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.fruitfarm;

import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfare.util.parsing.BlockStateMatcher;
import io.github.capttracker.ancientwarfare.util.parsing.PropertyState;
import io.github.capttracker.ancientwarfare.util.parsing.PropertyStateMatcher;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A fruit that is ripe when a block state property has a given value (read from the data file); picking takes what
 * the block would drop and sets the property to its "new" value, the plant itself stays.
 */
public class FruitPicked implements IFruit {
    private final BlockStateMatcher stateMatcher;
    private final PropertyStateMatcher ripeStateMatcher;
    private final PropertyState<?, ?> newState;

    public FruitPicked(BlockStateMatcher stateMatcher, PropertyStateMatcher ripeStateMatcher, PropertyState<?, ?> newState) {
        this.stateMatcher = stateMatcher;
        this.ripeStateMatcher = ripeStateMatcher;
        this.newState = newState;
    }

    @Override
    public boolean matches(BlockState state) {
        return stateMatcher.test(state);
    }

    @Override
    public boolean isRipe(BlockState state) {
        return ripeStateMatcher.test(state);
    }

    @Override
    public boolean pick(Level level, BlockState state, BlockPos pos, int fortune, IItemHandler inventory) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        NonNullList<ItemStack> drops = getDrops(serverLevel, state, pos, fortune);
        if (drops.isEmpty() || !InventoryTools.canInventoryHold(inventory, drops)) {
            return false;
        }
        level.setBlock(pos, newState.update(state), Block.UPDATE_ALL);
        putInInventory(level, pos, inventory, drops);
        return true;
    }

    protected NonNullList<ItemStack> getDrops(ServerLevel level, BlockState state, BlockPos pos, int fortune) {
        return InventoryTools.toNonNullList(WorksiteTools.getDrops(level, pos, state, fortune));
    }

    protected void putInInventory(Level level, BlockPos pos, IItemHandler inventory, NonNullList<ItemStack> drops) {
        InventoryTools.insertOrDropItems(inventory, drops, level, pos);
    }

    @Override
    public boolean isPlantable() {
        return false;
    }
}
