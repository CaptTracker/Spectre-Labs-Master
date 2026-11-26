/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9, builders): what a builder may break.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import io.github.capttracker.ancientwarfarenpc.settlement.JobBoard;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * The rules for what a builder may break when it clears a site, levels the ground or replaces a block of the design: only the terrain and plants (earth,
 * sand, stone, ore, trees, plants), only inside the plot of the build, never a block with a block entity (chests, worksites, signs, banners), never the
 * hall, a marker, a linked storage, a bed, a gathering point or a worksite, and never a block inside another build of the settlement. A step that breaks
 * a rule is skipped (the building is then a little different from its design there).
 */
public final class BuildSafety {
    private BuildSafety() {}

    /** True if the column is inside the plot of the project (a project without a plot is only limited by the land of the settlement). */
    public static boolean inPlot(Settlement settlement, BuildProject project, BlockPos pos) {
        if (project.getPlotId() == null) {
            return settlement.contains(pos);
        }
        return settlement.getBuilding().plot(project.getPlotId()).map(plot -> plot.contains(pos.getX(), pos.getZ())).orElse(false);
    }

    /** Whether the builder may break the block at the position (to clear it, or to put a block of the design in its place). Air and replaceable blocks are always fine. */
    public static boolean mayBreak(ServerLevel level, Settlement settlement, BuildProject project, BlockPos pos, BlockState current) {
        if (current.isAir() || current.canBeReplaced()) {
            return true;
        }
        if (current.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) { // chests, worksites, signs, banners: never
            return false;
        }
        if (isProtectedPoint(settlement, pos) || insideOtherBuild(settlement, project, pos)) {
            return false;
        }
        return isTerrain(level, pos, current);
    }

    private static boolean isProtectedPoint(Settlement settlement, BlockPos pos) {
        if (pos.equals(settlement.getHallPos()) || settlement.getMarkers().contains(pos) || settlement.isStorage(pos)
                || settlement.getBeds().contains(pos) || settlement.getGatherPoints().contains(pos)) {
            return true;
        }
        for (JobBoard.Job job : settlement.getJobBoard().getJobs()) {
            if (job.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /** True if the block is inside the box of another build of the settlement (planned, queued, under construction or finished). */
    private static boolean insideOtherBuild(Settlement settlement, BuildProject project, BlockPos pos) {
        for (BuildProject other : settlement.getBuilding().getProjects()) {
            if (other == project || other.getKind() != BuildProject.Kind.BUILD) {
                continue;
            }
            int[] size = other.getSize();
            BlockPos origin = other.getOrigin();
            if (pos.getX() >= origin.getX() && pos.getX() < origin.getX() + size[0] && pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + size[2]
                    && pos.getY() >= origin.getY() && pos.getY() < origin.getY() + size[1]) {
                return true;
            }
        }
        return false;
    }

    /** Earth, sand, stone, ore, snow, trees and plants (what the world made, as far as a block can tell). */
    private static boolean isTerrain(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.LEAVES) || state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.SNOW) || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS) || state.is(Tags.Blocks.ORES) || state.is(Tags.Blocks.STONES)) {
            return true;
        }
        if (state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.ICE) || state.is(Blocks.TUFF) || state.is(Blocks.CALCITE) || state.is(Blocks.DRIPSTONE_BLOCK) || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.MUD) || state.is(Blocks.MOSS_CARPET) || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.AMETHYST_BLOCK)) {
            return true;
        }
        if (state.is(BlockTags.LOGS)) {
            return treeAround(level, pos); // a log counts as a tree only with leaves nearby (a log cabin is somebody's building)
        }
        net.minecraft.world.level.block.Block block = state.getBlock();
        if (block instanceof CropBlock || block instanceof StemBlock) {
            return false; // somebody's field
        }
        return block instanceof BushBlock || block instanceof VineBlock || block instanceof CactusBlock || block instanceof SugarCaneBlock
                || state.is(Blocks.BAMBOO) || state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN) || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.MUSHROOM_STEM);
    }

    private static boolean treeAround(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -1; dy <= 4; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (level.isLoaded(cursor) && level.getBlockState(cursor).is(BlockTags.LEAVES)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
