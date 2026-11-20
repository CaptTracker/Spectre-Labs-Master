/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/block/BlockEngineeringStation and BlockResearchStation.
 */
package io.github.capttracker.ancientwarfare.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * The shared base of the engineering and research benches. Facing replaces the 1.12.2 metadata 0-3; the research
 * bench also has {@code has_book} (was metadata +4).
 *
 * <p>The block faces the player who placed it (its front is the side with the shelf knob in the models), as in
 * the original, which used the opposite of the placer's horizontal facing ({@code invertFacing}). The first port
 * of this class used the placer's own facing, which put the front away from the player; fixed here.
 */
public class StationBlock extends BaseBlock implements BlockRotationHandler.IRotatableBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty HAS_BOOK = BooleanProperty.create("has_book");

    private final boolean hasBookProperty;

    public StationBlock(Properties props, boolean hasBookProperty) {
        super(props);
        this.hasBookProperty = hasBookProperty;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HAS_BOOK, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // Both benches carry has_book so one class serves both; only the research bench ever sets it.
        builder.add(FACING, HAS_BOOK);
    }

    public boolean usesBookProperty() {
        return hasBookProperty;
    }

    @Override
    public BlockRotationHandler.RotationType getRotationType() {
        return BlockRotationHandler.RotationType.FOUR_WAY;
    }

    @Override
    public boolean invertFacing() {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, BlockRotationHandler.getFaceForPlacement(context, this));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
