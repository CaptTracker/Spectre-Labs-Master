/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/torque/TileConduitHeavy.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.torque;

import io.github.capttracker.ancientwarfareautomation.block.torque.TorqueTier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** The heavy tier of the TileTorqueSidedCell: efficiency and transfer come from {@link TorqueTier#HEAVY}. */
public final class TileConduitHeavy extends TileTorqueSidedCell {

    public TileConduitHeavy(BlockPos pos, BlockState state) {
        super(TorqueTier.HEAVY, pos, state);
    }

}
