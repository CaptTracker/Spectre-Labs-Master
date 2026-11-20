/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: lets the torque block entities find their registered type without referring to the registry
 * class (the registry entries are declared by the base slice).
 */
package io.github.capttracker.ancientwarfareautomation.block.torque;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Implemented by every torque block. The block gets the (lazily resolved) block entity type from whoever registers
 * it, and the block entities pass {@link #typeOf(BlockState)} to their super constructor.
 */
public interface IBlockEntityTypeProvider {
    /** The registered type of the block entity this block creates. */
    BlockEntityType<?> getBlockEntityType();

    /** The type for the block entity of the given state (the block of the state must implement this interface). */
    static BlockEntityType<?> typeOf(BlockState state) {
        if (state.getBlock() instanceof IBlockEntityTypeProvider provider) {
            return provider.getBlockEntityType();
        }
        throw new IllegalStateException("Block " + state.getBlock() + " does not provide a block entity type");
    }
}
