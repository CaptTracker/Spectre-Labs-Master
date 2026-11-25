/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockStirlingGenerator.
 */
package io.github.capttracker.ancientwarfareautomation.block.torque;

import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileStirlingGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * The stirling generator ({@code stirling_generator}): burns fuel for torque, one fuel slot. Right-click opens the menu
 * (the block entity does that, through {@code TileStirlingGenerator.setMenuFactory}); the contents drop when the
 * block is broken (the block entity is an {@code IBlockBreakHandler}, called by {@code BaseBlock}).
 */
public class BlockStirlingGenerator extends BlockTorqueGenerator {

    public BlockStirlingGenerator(BlockBehaviour.Properties properties, Supplier<? extends BlockEntityType<?>> blockEntityType) {
        super(properties, blockEntityType);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileStirlingGenerator(pos, state);
    }
}
