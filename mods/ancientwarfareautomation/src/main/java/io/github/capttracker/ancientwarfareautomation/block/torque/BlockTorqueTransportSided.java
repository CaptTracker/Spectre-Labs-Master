/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockTorqueTransportSided.
 */
package io.github.capttracker.ancientwarfareautomation.block.torque;

import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileTorqueSidedCell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Supplier;

/**
 * Base of the blocks with one torque cell per side (junction, distributor). The six unlisted
 * {@code connection_<SIDE>} properties of 1.12.2 are gone: a renderer or shape asks the block entity
 * ({@link TileTorqueSidedCell#getConnections()}), and {@link #isConnected} does that lookup for a block position.
 * (Real block state properties would make every neighbour change a block update; the original never exposed them.)
 */
public abstract class BlockTorqueTransportSided extends BlockTorqueTransport {

    protected BlockTorqueTransportSided(BlockBehaviour.Properties properties, TorqueTier tier, Supplier<? extends BlockEntityType<?>> blockEntityType) {
        super(properties, tier, blockEntityType);
    }

    /** Whether the side of the block at the position is connected (false if there is no such block entity). */
    public static boolean isConnected(BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof TileTorqueSidedCell tile && tile.isConnected(side);
    }

}
