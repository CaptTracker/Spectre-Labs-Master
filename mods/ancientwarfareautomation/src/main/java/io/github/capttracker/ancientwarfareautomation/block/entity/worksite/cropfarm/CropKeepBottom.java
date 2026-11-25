/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/cropfarm/CropKeepBottom.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm;

import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfare.util.parsing.BlockStateMatcher;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteTools;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sugar cane and cactus: everything above the bottom block is harvested, the bottom block stays and keeps growing.
 * The bottom block is the position that is handed in (it is queued as soon as there is a matching block above it);
 * the blocks to break are found at harvest time, so a plant that grew a block meanwhile is harvested whole.
 */
public class CropKeepBottom extends CropDefault {
    private static final int MAX_HEIGHT = 10;
    private final BlockStateMatcher stateMatcher;

    public CropKeepBottom(BlockStateMatcher stateMatcher) {
        this.stateMatcher = stateMatcher;
    }

    @Override
    public boolean harvest(Level level, BlockState state, BlockPos pos, int fortune, IItemHandler inventory) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        List<ItemStack> stacks = new ArrayList<>();
        //figuring out positions here instead of in getPositionsToHarvest because if growing quickly we could be harvesting block that's not the top one.
        // so instead just pass in the bottom one as the one to harvest and then figure out all of the blocks to harvest at once here
        Map<BlockPos, BlockState> harvestPositions = getHarvestPositions(level, pos);
        for (Map.Entry<BlockPos, BlockState> entry : harvestPositions.entrySet()) {
            stacks.addAll(WorksiteTools.getDrops(serverLevel, entry.getKey(), entry.getValue(), fortune));
        }
        if (!InventoryTools.canInventoryHold(inventory, stacks)) {
            return false;
        }
        for (Map.Entry<BlockPos, BlockState> entry : harvestPositions.entrySet()) {
            if (!breakCrop(level, entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        InventoryTools.insertOrDropItems(inventory, stacks, level, pos);
        return true;
    }

    /** The matching blocks above the bottom one (at most 9), highest first. */
    private Map<BlockPos, BlockState> getHarvestPositions(Level level, BlockPos pos) {
        Map<BlockPos, BlockState> ret = new TreeMap<>(Collections.reverseOrder());
        for (BlockPos currentPos = pos.above(); currentPos.getY() < pos.getY() + MAX_HEIGHT; currentPos = currentPos.above()) {
            BlockState state = level.getBlockState(currentPos);
            if (!stateMatcher.test(state)) {
                break;
            }
            ret.put(currentPos, state);
        }
        return ret;
    }

    @Override
    public List<BlockPos> getPositionsToHarvest(Level level, BlockPos pos, BlockState state) {
        if (stateMatcher.test(level.getBlockState(pos.above()))) {
            return Collections.singletonList(pos);
        }
        return Collections.emptyList();
    }

    @Override
    public boolean canBeFertilized(BlockState state, Level level, BlockPos pos) {
        return false;
    }

    @Override
    public boolean matches(BlockState state) {
        return stateMatcher.test(state);
    }

    @Override
    public boolean matches(ItemStack stack) {
        return false;
    }
}
