/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/fruitfarm/FruitBreakOnly.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.fruitfarm;

import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfare.util.parsing.BlockStateMatcher;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteTools;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/** A fruit that is always ripe and is picked by breaking the block (the drops are taken, nothing is replanted). */
public class FruitBreakOnly implements IFruit {
    private final BlockStateMatcher stateMatcher;

    public FruitBreakOnly(BlockStateMatcher stateMatcher) {
        this.stateMatcher = stateMatcher;
    }

    @Override
    public boolean matches(BlockState state) {
        return stateMatcher.test(state);
    }

    @Override
    public boolean isRipe(BlockState state) {
        return true;
    }

    @Override
    public boolean pick(Level level, BlockState state, BlockPos pos, int fortune, IItemHandler inventory) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        List<ItemStack> drops = WorksiteTools.getDrops(serverLevel, pos, state, fortune);
        if (drops.isEmpty() || !InventoryTools.canInventoryHold(inventory, drops)) {
            return false;
        }
        // 1.12.2 BlockTools.breakBlock(world, pos, fortune, false): removes the block without drops or a break event
        BlockTools.breakBlock(level, pos, fortune, false);
        InventoryTools.insertOrDropItems(inventory, drops, level, pos);
        return true;
    }

    @Override
    public boolean isPlantable() {
        return false;
    }
}
