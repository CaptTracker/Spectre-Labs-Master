/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/cropfarm/CropTall.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm;

import io.github.capttracker.ancientwarfare.util.parsing.BlockStateMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * A crop made of several blocks on top of each other (a fixed height from the data file), harvested as a whole.
 * Every block of the stack that is mature is a harvest position; breaking and the drops cover all {@code height}
 * blocks from the top down.
 */
public class CropTall extends CropDefault {
    private final int height;
    private final BlockStateMatcher stateMatcher;

    public CropTall(BlockStateMatcher stateMatcher, int height) {
        this.stateMatcher = stateMatcher;
        this.height = height;
    }

    @Override
    public List<BlockPos> getPositionsToHarvest(Level level, BlockPos pos, BlockState state) {
        List<BlockPos> ret = new ArrayList<>();
        applyToCrop(level, pos, (p, s) -> {
            if (isMature(s)) {
                ret.add(p);
            }
            return true;
        });
        return ret;
    }

    @Override
    protected boolean breakCrop(Level level, BlockPos pos, BlockState state) {
        return applyToCrop(level, pos, (p, s) -> super.breakCrop(level, p, s));
    }

    @Override
    protected List<ItemStack> getDrops(ServerLevel level, BlockPos pos, BlockState state, int fortune) {
        List<ItemStack> ret = new ArrayList<>();
        applyToCrop(level, pos, (p, s) -> {
            ret.addAll(super.getDrops(level, p, s, fortune));
            return true;
        });
        return ret;
    }

    /** Applies the function to the blocks of the stack, from the top down; stops counting as success at the first failure (all are still visited). */
    private boolean applyToCrop(Level level, BlockPos pos, BiFunction<BlockPos, BlockState, Boolean> applyToBlock) {
        boolean ret = true;
        for (BlockPos curPos = new BlockPos(pos.getX(), pos.getY() + (height - 1), pos.getZ()); curPos.getY() >= pos.getY(); curPos = curPos.below()) {
            BlockState curState = level.getBlockState(curPos);
            ret = ret && applyToBlock.apply(curPos, curState);
        }
        return ret;
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
