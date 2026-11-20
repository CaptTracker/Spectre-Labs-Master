/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/block/BlockGateProxy.
 */
package io.github.capttracker.ancientwarfarestructure.block;

import io.github.capttracker.ancientwarfarestructure.block.entity.GateProxyBlockEntity;
import io.github.capttracker.ancientwarfarestructure.init.AwGates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The invisible block that fills every cell of a gate and makes it solid. Unbreakable, no drops, not selectable. The
 * gate entity ({@code EntityGate}) owns it: it sets the {@link #MODE} of every proxy when the gate starts to open, has
 * closed or (drawbridge) has swung down, and the shape follows the mode.
 *
 * <p>1.12.2 chose the axis of the closed wall from the neighbouring proxies and asked the owner entity whether the gate
 * was closed on every collision query. Now the long axis of the gate is the {@link #AXIS} state (the gate knows it) and
 * the open state is the {@link #MODE}, so a shape is a pure function of the block state, as 1.21 requires.
 */
public final class GateProxyBlock extends Block implements EntityBlock {
    /** The axis the gate is long on: the closed wall spans this axis. */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<GateProxyMode> MODE = EnumProperty.create("mode", GateProxyMode.class);

    // The 1.12.2 box had no thickness (8/16 on one axis); a shape needs some, so the wall is as thick as the gate model's panel.
    private static final VoxelShape CLOSED_ALONG_X = Block.box(0, 0, 7, 16, 16, 9);
    private static final VoxelShape CLOSED_ALONG_Z = Block.box(7, 0, 0, 9, 16, 16);
    private static final VoxelShape LINTEL = Block.box(0, 8, 0, 16, 16, 16);

    public GateProxyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(MODE, GateProxyMode.CLOSED));
    }

    /** 1.12.2: Material.ROCK, unbreakable, resistance 6,000,000, no drops, not a full or opaque cube. */
    public static BlockBehaviour.Properties proxyProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .sound(SoundType.STONE)
                .strength(-1.0F, 6000000.0F)
                .noLootTable()
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK)
                .isValidSpawn((state, level, pos, type) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false);
    }

    /** The state of a proxy for a gate that is long on {@code axis}. */
    public BlockState stateFor(Direction.Axis axis, GateProxyMode mode) {
        return defaultBlockState().setValue(AXIS, axis).setValue(MODE, mode);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, MODE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** The outline: nothing (1.12.2 selected box was empty), so the player's pick ray reaches the gate entity behind it. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(MODE)) {
            case CLOSED -> state.getValue(AXIS) == Direction.Axis.X ? CLOSED_ALONG_X : CLOSED_ALONG_Z;
            case LINTEL -> LINTEL;
            case FLOOR -> Shapes.block();
            case OPEN -> Shapes.empty();
        };
    }

    /**
     * Mob path finding (1.12.2 {@code isPassable}): a shut gate blocks, a lowered drawbridge floor is ground, anything open
     * can be walked through. The original also required proxy neighbours on both sides of an open gate; the collision
     * shapes decide that now.
     */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        GateProxyMode mode = state.getValue(MODE);
        return switch (type) {
            case LAND, AIR -> mode == GateProxyMode.OPEN || mode == GateProxyMode.LINTEL;
            case WATER -> false;
        };
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GateProxyBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != AwGates.GATE_PROXY_BE.get()) {
            return null;
        }
        return (tickLevel, tickPos, tickState, blockEntity) -> ((GateProxyBlockEntity) blockEntity).serverTick();
    }
}
