/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockHandCrankedGenerator.
 */
package io.github.capttracker.ancientwarfareautomation.block.torque;

import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RotationType;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileHandCrankedGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * The hand cranked generator ({@code hand_cranked_generator}): cranked by a player with a hammer in work mode or by an NPC
 * worker (the block entity is an {@code IWorkSite}); the output side is the front.
 */
public class BlockHandCrankedGenerator extends BlockTorqueBase {

    public BlockHandCrankedGenerator(BlockBehaviour.Properties properties, Supplier<? extends BlockEntityType<?>> blockEntityType) {
        super(properties, blockEntityType);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileHandCrankedGenerator(pos, state);
    }

    @Override
    public RotationType getRotationType() {
        return RotationType.FOUR_WAY;
    }

    @Override
    public boolean invertFacing() {
        return false;
    }
}
