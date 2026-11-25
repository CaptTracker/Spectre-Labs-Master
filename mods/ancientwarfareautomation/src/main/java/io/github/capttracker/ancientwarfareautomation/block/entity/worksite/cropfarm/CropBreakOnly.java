/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/cropfarm/CropBreakOnly.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm;

import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfare.util.parsing.BlockStateMatcher;
import io.github.capttracker.ancientwarfare.util.parsing.ItemStackMatcher;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteTools;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * A crop that is simply broken, whenever it is there, and not replanted (mushrooms, melons and pumpkins).
 *
 * <p>Fixed bug: the original's {@code CropGourd} had no matchers and {@code matches(ItemStack)} threw a
 * NullPointerException for it; a handler without an item matcher matches no item now.
 */
public class CropBreakOnly implements ICrop {
    @Nullable
    private final BlockStateMatcher stateMatcher;
    @Nullable
    private final ItemStackMatcher stackMatcher;

    /** For subclasses that decide by themselves (the gourds). */
    CropBreakOnly() {
        this(null, null);
    }

    public CropBreakOnly(@Nullable BlockStateMatcher stateMatcher, @Nullable ItemStackMatcher stackMatcher) {
        this.stateMatcher = stateMatcher;
        this.stackMatcher = stackMatcher;
    }

    @Override
    public List<BlockPos> getPositionsToHarvest(Level level, BlockPos pos, BlockState state) {
        return Collections.singletonList(pos);
    }

    @Override
    public boolean canBeFertilized(BlockState state, Level level, BlockPos pos) {
        return false;
    }

    @Override
    public boolean harvest(Level level, BlockState state, BlockPos pos, int fortune, IItemHandler inventory) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        List<ItemStack> stacks = WorksiteTools.getDrops(serverLevel, pos, state, fortune);
        if (!InventoryTools.canInventoryHold(inventory, stacks)) {
            return false;
        }
        if (!BlockTools.breakBlockNoDrops(level, pos, state)) {
            return false;
        }
        InventoryTools.insertOrDropItems(inventory, stacks, level, pos);
        return true;
    }

    @Override
    public boolean matches(BlockState state) {
        return stateMatcher != null && stateMatcher.test(state);
    }

    @Override
    public boolean matches(ItemStack stack) {
        return stackMatcher != null && stackMatcher.test(stack);
    }

    @Override
    public boolean isPlantable(ItemStack stack) {
        return false;
    }
}
