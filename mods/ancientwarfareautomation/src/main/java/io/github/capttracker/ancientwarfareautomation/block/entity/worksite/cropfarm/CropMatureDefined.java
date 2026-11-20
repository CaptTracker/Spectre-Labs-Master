/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/cropfarm/CropMatureDefined.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm;

import io.github.capttracker.ancientwarfare.util.parsing.BlockStateMatcher;
import io.github.capttracker.ancientwarfare.util.parsing.PropertyStateMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.List;

/**
 * A crop whose maturity is a block state property value given in the data file (nether wart: {@code age} 3).
 * Harvested and replanted like the default crop.
 *
 * <p>Like the other data driven handlers it does not claim items ({@code matches(ItemStack)} is false), so an
 * item that no break-only handler knows is judged by the fallback handler. In 1.12.2 these handlers inherited the
 * default's "matches every item", which made the result depend on the order of the data files.
 */
public class CropMatureDefined extends CropDefault {
    private final BlockStateMatcher stateMatcher;
    private final PropertyStateMatcher matureStateMatcher;

    public CropMatureDefined(BlockStateMatcher stateMatcher, PropertyStateMatcher matureStateMatcher) {
        this.stateMatcher = stateMatcher;
        this.matureStateMatcher = matureStateMatcher;
    }

    @Override
    public boolean matches(BlockState state) {
        return stateMatcher.test(state);
    }

    @Override
    public boolean matches(ItemStack stack) {
        return false;
    }

    @Override
    public List<BlockPos> getPositionsToHarvest(Level level, BlockPos pos, BlockState state) {
        return matureStateMatcher.test(state) ? Collections.singletonList(pos) : Collections.emptyList();
    }
}
