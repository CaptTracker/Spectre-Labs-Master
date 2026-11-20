/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockTorqueDistributor.
 */
package io.github.capttracker.ancientwarfareautomation.block.torque;

import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileDistributorHeavy;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileDistributorLight;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileDistributorMedium;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/** The distributor ({@code torque_distributor_light}, {@code _medium}, {@code _heavy}): one input at the back, five outputs. */
public class BlockTorqueDistributor extends BlockTorqueTransportSided {

    public BlockTorqueDistributor(BlockBehaviour.Properties properties, TorqueTier tier, Supplier<? extends BlockEntityType<?>> blockEntityType) {
        super(properties, tier, blockEntityType);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (getTier()) {
            case LIGHT -> new TileDistributorLight(pos, state);
            case MEDIUM -> new TileDistributorMedium(pos, state);
            case HEAVY -> new TileDistributorHeavy(pos, state);
        };
    }
}
