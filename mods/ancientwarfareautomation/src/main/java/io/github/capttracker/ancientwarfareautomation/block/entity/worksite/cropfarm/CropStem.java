/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/cropfarm/CropStem.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Melon and pumpkin stems: the stem itself is never harvested, the gourds next to it are (the position list holds
 * the gourds among the four horizontal neighbours). The stem is fertilised while it can still grow.
 *
 * <p>1.12.2 had one stem block; 1.21 has the growing {@link StemBlock} and the {@link AttachedStemBlock} that
 * replaces it once a gourd has grown, both belong to this handler.
 */
public class CropStem implements ICrop {
    @Override
    public List<BlockPos> getPositionsToHarvest(Level level, BlockPos pos, BlockState state) {
        List<BlockPos> ret = new ArrayList<>();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            addPositionIfGourd(ret, level, pos.relative(side));
        }
        return ret;
    }

    @Override
    public boolean canBeFertilized(BlockState state, Level level, BlockPos pos) {
        return state.getBlock() instanceof StemBlock stem && stem.isValidBonemealTarget(level, pos, state);
    }

    private void addPositionIfGourd(List<BlockPos> list, Level level, BlockPos pos) {
        if (CropGourd.isGourd(level.getBlockState(pos))) {
            list.add(pos);
        }
    }

    @Override
    public boolean harvest(Level level, BlockState state, BlockPos pos, int fortune, IItemHandler inventory) {
        return false;
    }

    @Override
    public boolean matches(BlockState state) {
        return state.getBlock() instanceof StemBlock || state.getBlock() instanceof AttachedStemBlock;
    }
}
