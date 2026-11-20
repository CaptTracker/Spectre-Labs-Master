/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/item/ItemBlockOwned, ItemBlockOwnedRotatable and ItemBlockRotatableMetaTile.
 */
package io.github.capttracker.ancientwarfare.item;

import io.github.capttracker.ancientwarfare.block.BlockRotationHandler;
import io.github.capttracker.ancientwarfare.owner.IOwnable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block item that makes the placer the owner of the placed block entity ({@link IOwnable}) and, for a block
 * entity that keeps its own facing ({@link BlockRotationHandler.IRotatableTile}) on a rotatable block, sets that
 * facing from the placer. One class replaces the three 1.12.2 variants. A block that keeps its facing in the block
 * state needs no item for that: its {@code getStateForPlacement} uses
 * {@link BlockRotationHandler#getFaceForPlacement(BlockPlaceContext, BlockRotationHandler.IRotatableBlock)}.
 *
 * <p>{@code ItemBlockBase} is a plain {@link BlockItem} now (registered by {@code ModRegistrar.block}) and
 * {@code ItemBlockMeta} is gone with item metadata.
 *
 * <p>Use with {@code REG.blockWithoutItem(...)} plus {@code REG.item(name, p -> new OwnedBlockItem(block.get(), p))}.
 * A block whose own {@code setPlacedBy} sets the owner (like the research bench) does not need it.
 */
public class OwnedBlockItem extends BlockItem {
    public OwnedBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        boolean placed = super.placeBlock(context, state);
        if (placed) {
            BlockEntity blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
            Player player = context.getPlayer();
            if (blockEntity instanceof IOwnable ownable && player != null) {
                ownable.setOwner(player);
            }
            if (blockEntity instanceof BlockRotationHandler.IRotatableTile rotatable && getBlock() instanceof BlockRotationHandler.IRotatableBlock rotatableBlock) {
                rotatable.setPrimaryFacing(BlockRotationHandler.getFaceForPlacement(context, rotatableBlock));
            }
        }
        return placed;
    }
}
