/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockWaterwheelGenerator.
 */
package io.github.capttracker.ancientwarfareautomation.block.torque;

import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileWaterwheelGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.function.Supplier;

/**
 * The waterwheel generator ({@code waterwheel_generator}). Placed with its front towards the player, so the wheel
 * (on the back) points away; needs a water channel in front of the wheel. {@code valid_setup} is a block state
 * property that the block entity keeps in step with its validity check (the 1.12.2 block declared it but never set it).
 */
public class BlockWaterwheelGenerator extends BlockTorqueGenerator {
    public static final BooleanProperty VALID_SETUP = BooleanProperty.create("valid_setup");

    public BlockWaterwheelGenerator(BlockBehaviour.Properties properties, Supplier<? extends BlockEntityType<?>> blockEntityType) {
        super(properties, blockEntityType);
        registerDefaultState(defaultBlockState().setValue(VALID_SETUP, false));
    }

    @Override
    protected void addProperties(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VALID_SETUP);
    }

    @Override
    public boolean invertFacing() {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileWaterwheelGenerator(pos, state);
    }
}
