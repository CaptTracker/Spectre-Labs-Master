/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/block/BlockBase and BlockBaseCore (the creative tab and model registration
 * of BlockBaseCore are done by the registrar and JSON assets now).
 */
package io.github.capttracker.ancientwarfare.block;

import io.github.capttracker.ancientwarfare.api.IBlockBreakHandler;
import io.github.capttracker.ancientwarfare.api.IInteractableTile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Base of Ancient Warfare blocks with block entities. Adds the two hooks the original base class had:
 * <ul>
 *   <li>{@link IBlockBreakHandler#onBlockBroken} on the block entity when the block is removed;</li>
 *   <li>{@link IInteractableTile#onBlockClicked} on the block entity when the block is right-clicked
 *   (was {@code WorldTools.clickInteractableTileWithHand} from {@code onBlockActivated}).</li>
 * </ul>
 * The 1.12.2 default hardness of 2 is a {@code Block.Properties} setting now ({@code strength(2f)}).
 */
public abstract class BaseBlock extends Block {
    protected BaseBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof IInteractableTile tile && tile.onBlockClicked(player, hand)) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Only when the block goes away; a state change (for example has_book) keeps the block entity and its contents.
        if (!state.is(newState.getBlock()) && state.hasBlockEntity() && level.getBlockEntity(pos) instanceof IBlockBreakHandler handler) {
            handler.onBlockBroken(state);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
