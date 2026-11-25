/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockTorqueBase (and BlockBaseAutomation).
 */
package io.github.capttracker.ancientwarfareautomation.block.torque;

import io.github.capttracker.ancientwarfare.block.BaseBlock;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.IRotatableBlock;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RotationType;
import io.github.capttracker.ancientwarfare.owner.IOwnable;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileTorqueBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Base of the torque blocks. Compared with the 1.12.2 block:
 * <ul>
 *   <li>the facing is the block state property {@code facing} (six directions for {@link RotationType#SIX_WAY}
 *   blocks, horizontal ones for {@link RotationType#FOUR_WAY}); the block entity reads it from there. The hammer
 *   turns it as it turns every block with a {@code facing} property (the 1.12.2 {@code rotateBlock});</li>
 *   <li>the unlisted properties for the baked model (rotations, active, dynamic) are gone: a block entity renderer
 *   reads the block entity (see {@link TileTorqueBase#getClientOutputRotation});</li>
 *   <li>the block entity type is passed in as a supplier, because the registry entries are declared elsewhere;</li>
 *   <li>the placer becomes the owner of block entities that are {@link IOwnable} (was {@code ItemBlockTorqueTile}).</li>
 * </ul>
 *
 * <p>Neighbour changes reach the block entity through {@code neighborChanged} and {@code onNeighborChange}, which
 * clear its neighbour cache. The block ticks its block entity on both sides.
 */
public abstract class BlockTorqueBase extends BaseBlock implements IRotatableBlock, EntityBlock, IBlockEntityTypeProvider {

    private final Supplier<? extends BlockEntityType<?>> blockEntityType;

    /**
     * Properties the base slice can use for these blocks: 1.12.2 had {@code Material.ROCK} and hardness 2, and they are
     * not opaque or full cubes. (The block entity renderer draws them; the 1.12.2 "tool required" of rock is left out,
     * see the port notes.)
     */
    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0F).sound(SoundType.STONE).noOcclusion();
    }

    protected BlockTorqueBase(BlockBehaviour.Properties properties, Supplier<? extends BlockEntityType<?>> blockEntityType) {
        super(properties);
        this.blockEntityType = blockEntityType;
        registerDefaultState(stateDefinition.any().setValue(getFacingProperty(), Direction.NORTH));
    }

    @Override
    public BlockEntityType<?> getBlockEntityType() {
        return blockEntityType.get();
    }

    // ---- facing ----

    /** The {@code facing} property of this block: all six directions for six-way blocks, the four horizontal ones otherwise. */
    public final DirectionProperty getFacingProperty() {
        return getRotationType() == RotationType.SIX_WAY ? BlockStateProperties.FACING : BlockStateProperties.HORIZONTAL_FACING;
    }

    /** The facing of any state of a torque block (north if the state has none). */
    public static Direction getFacing(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        return Direction.NORTH;
    }

    /** The state turned to the direction; unchanged if the block cannot face that way (a four-way block and up or down). */
    public static BlockState withFacing(BlockState state, Direction facing) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.setValue(BlockStateProperties.FACING, facing);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && facing.getAxis().isHorizontal()) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        return state;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(getFacingProperty());
        addProperties(builder);
    }

    /** Subclasses add their own state properties here (the waterwheel adds {@code valid_setup}). */
    protected void addProperties(StateDefinition.Builder<Block, BlockState> builder) {

    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(getFacingProperty(), BlockRotationHandler.getFaceForPlacement(context, this));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        DirectionProperty facing = getFacingProperty();
        return state.setValue(facing, rotation.rotate(state.getValue(facing)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(getFacingProperty())));
    }

    // ---- placing ----

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof IOwnable ownable) {
            ownable.setOwner(player);
        }
    }

    // ---- ticking ----

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != blockEntityType.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof TileTorqueBase torque) {
                torque.update();
            }
        };
    }

    // ---- neighbours ----

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof TileTorqueBase torque) {
            torque.onNeighborTileChanged();
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        if (level.getBlockEntity(pos) instanceof TileTorqueBase torque) {
            torque.onNeighborTileChanged();
        }
        super.onNeighborChange(state, level, pos, neighbor);
    }
}
