/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockWarehouseStockViewer.
 */
package io.github.capttracker.ancientwarfareautomation.block.warehouse;

import io.github.capttracker.ancientwarfare.block.BaseBlock;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.ControlledBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStockViewerBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * The warehouse stock viewer ({@code warehouse_stock_viewer}): a flat sign on one side of the block space that shows
 * counts from a warehouse (drawn by a block entity renderer of the client slice). No collision. The facing is the
 * direction the placer looks in, as in the original; the owner is set by the block item ({@code OwnedBlockItem}).
 * Right-click (owner, team mates and friends only) opens the filter screen, done by the block entity.
 */
public class WarehouseStockViewerBlock extends BaseBlock implements EntityBlock, BlockRotationHandler.IRotatableBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** The plate against one wall of the block space (1.12.2 AABBS: 0.125 thick, from 0.375 to 0.875 in height). */
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.WEST, Block.box(14, 6, 0, 16, 14, 16),
            Direction.EAST, Block.box(0, 6, 0, 2, 14, 16),
            Direction.SOUTH, Block.box(0, 6, 0, 16, 14, 2),
            Direction.NORTH, Block.box(0, 6, 14, 16, 14, 16));

    public WarehouseStockViewerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
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
        return new WarehouseStockViewerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return blockEntityType == AwAutomationBase.WAREHOUSE_STOCK_VIEWER_BE.get() ? ControlledBlockEntity.ticker(level) : null;
    }
}
