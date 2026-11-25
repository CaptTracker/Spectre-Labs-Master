/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockMailbox.
 */
package io.github.capttracker.ancientwarfareautomation.block.warehouse;

import io.github.capttracker.ancientwarfare.block.BaseBlock;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseMenus;
import io.github.capttracker.ancientwarfareautomation.gamedata.MailboxBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The mailbox ({@code mailbox}): a block with a send and a receive inventory that trades items with other mailboxes
 * through {@code MailboxData}. Right-click opens the mailbox screen on the server side
 * ({@link WarehouseMenus#openMailbox}); the contents drop when the block is broken (the block entity is an
 * {@code IBlockBreakHandler}), the block entity's ticker moves the mail ({@code MailboxBlockEntity.ticker}) and its
 * item handler is a block capability ({@code AwAutomationBase}).
 *
 * <p>Facing: the block faces the player who placed it ({@code invertFacing}). In 1.12.2 the block state got the
 * placer's direction and the block item then overwrote it with the inverted one through the tile's
 * {@code setPrimaryFacing}, so the placed result is the same.
 */
public class MailboxBlock extends BaseBlock implements EntityBlock, BlockRotationHandler.IRotatableBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public MailboxBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, BlockRotationHandler.getFaceForPlacement(context, this));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MailboxBlockEntity mailbox) {
            WarehouseMenus.openMailbox(player, mailbox);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockRotationHandler.RotationType getRotationType() {
        return BlockRotationHandler.RotationType.FOUR_WAY;
    }

    @Override
    public boolean invertFacing() {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MailboxBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return blockEntityType == AwAutomationBase.MAILBOX_BE.get() ? MailboxBlockEntity.ticker(level) : null;
    }
}
