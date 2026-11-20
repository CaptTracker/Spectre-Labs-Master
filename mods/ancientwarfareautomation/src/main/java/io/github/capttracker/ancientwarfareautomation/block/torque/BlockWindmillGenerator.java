/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockWindmillGenerator.
 */
package io.github.capttracker.ancientwarfareautomation.block.torque;

import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileWindmillController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * The windmill output block ({@code windmill_generator}). Placed with its front towards the player, so the blade plane
 * has to be built directly behind it (the controller takes the energy from the centre blade behind it).
 */
public class BlockWindmillGenerator extends BlockTorqueGenerator {

    public BlockWindmillGenerator(BlockBehaviour.Properties properties, Supplier<? extends BlockEntityType<?>> blockEntityType) {
        super(properties, blockEntityType);
    }

    @Override
    public boolean invertFacing() {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileWindmillController(pos, state);
    }
}
