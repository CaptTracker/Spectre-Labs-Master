/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/item/ItemBlockWorksiteStatic.
 */
package io.github.capttracker.ancientwarfareautomation.item;

import io.github.capttracker.ancientwarfare.api.IBoundedSite;
import io.github.capttracker.ancientwarfare.item.OwnedBlockItem;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The item of the worksite blocks (quarry, farms, warehouse control block). Placing it makes the placer the owner
 * and sets the facing ({@link OwnedBlockItem}) and gives the worksite a default 5 x 5 work area in front of the
 * player: it starts one block ahead and two to the left and ends four blocks ahead and four to the right of the
 * block (seen from the player).
 *
 * <p>The original has two TODOs about checking that the area does not overlap other worksites; they are still open.
 */
public class WorksiteBlockItem extends OwnedBlockItem {
    public WorksiteBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        boolean placed = super.placeBlock(context, state);
        if (placed) {
            Level level = context.getLevel();
            Player player = context.getPlayer();
            BlockPos pos = context.getClickedPos();
            if (!level.isClientSide && player != null) {
                Direction playerFacing = player.getDirection();
                BlockPos pos1 = pos.relative(playerFacing).relative(playerFacing.getCounterClockWise(), 2);
                BlockPos pos2 = pos.relative(playerFacing, 4).relative(playerFacing.getClockWise(), 4);
                /*
                 * TODO validate that block is not inside work bounds of any other nearby worksites ??
                 * TODO validate that worksite does not intersect any others
                 */
                BlockEntity worksite = level.getBlockEntity(pos);
                if (worksite instanceof IBoundedSite site) {
                    site.setBounds(pos1, pos2);
                }
                BlockTools.notifyBlockUpdate(level, pos);
            }
        }
        return placed;
    }
}
