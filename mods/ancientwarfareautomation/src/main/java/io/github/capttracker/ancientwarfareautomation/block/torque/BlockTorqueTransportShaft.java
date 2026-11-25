/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockTorqueTransportShaft.
 */
package io.github.capttracker.ancientwarfareautomation.block.torque;

import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileTorqueShaftHeavy;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileTorqueShaftLight;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileTorqueShaftMedium;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * A straight shaft ({@code torque_shaft_light}, {@code _medium}, {@code _heavy}). The shape stretches along the facing
 * axis. {@code has_previous} and {@code has_next} of the 1.12.2 model are {@code TileTorqueShaft#hasPrevious()} and
 * {@code hasNext()}.
 */
public class BlockTorqueTransportShaft extends BlockTorqueTransport {
    private static final float MIN = 0.1875f;
    private static final float MAX = 0.8125f;
    private static final VoxelShape SHAPE_X = Shapes.box(0, MIN, MIN, 1, MAX, MAX);
    private static final VoxelShape SHAPE_Y = Shapes.box(MIN, 0, MIN, MAX, 1, MAX);
    private static final VoxelShape SHAPE_Z = Shapes.box(MIN, MIN, 0, MAX, MAX, 1);

    public BlockTorqueTransportShaft(BlockBehaviour.Properties properties, TorqueTier tier, Supplier<? extends BlockEntityType<?>> blockEntityType) {
        super(properties, tier, blockEntityType);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (getTier()) {
            case LIGHT -> new TileTorqueShaftLight(pos, state);
            case MEDIUM -> new TileTorqueShaftMedium(pos, state);
            case HEAVY -> new TileTorqueShaftHeavy(pos, state);
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = getFacing(state);
        return switch (facing.getAxis()) {
            case X -> SHAPE_X;
            case Y -> SHAPE_Y;
            case Z -> SHAPE_Z;
        };
    }
}
