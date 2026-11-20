/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockTorqueJunction.
 */
package io.github.capttracker.ancientwarfareautomation.block.torque;

import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileConduitHeavy;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileConduitLight;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileConduitMedium;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/** The conduit ({@code torque_junction_light}, {@code _medium}, {@code _heavy}): one output side, five input sides. */
public class BlockTorqueJunction extends BlockTorqueTransportSided {

    public BlockTorqueJunction(BlockBehaviour.Properties properties, TorqueTier tier, Supplier<? extends BlockEntityType<?>> blockEntityType) {
        super(properties, tier, blockEntityType);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (getTier()) {
            case LIGHT -> new TileConduitLight(pos, state);
            case MEDIUM -> new TileConduitMedium(pos, state);
            case HEAVY -> new TileConduitHeavy(pos, state);
        };
    }
}
