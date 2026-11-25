/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/tile/IBlockBreakHandler.
 */
package io.github.capttracker.ancientwarfare.api;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A block entity that must do something when its block is removed (usually drop its inventory). Called by
 * {@code BaseBlock#onRemove}, before the block entity is removed, only when the block really goes away (not when
 * its state changes).
 */
public interface IBlockBreakHandler {
    void onBlockBroken(BlockState state);
}
