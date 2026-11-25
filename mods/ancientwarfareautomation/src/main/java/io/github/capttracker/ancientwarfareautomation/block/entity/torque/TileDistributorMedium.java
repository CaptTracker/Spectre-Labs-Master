/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/torque/TileDistributorMedium.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.torque;

import io.github.capttracker.ancientwarfareautomation.block.torque.TorqueTier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** The medium tier of the TileDistributor: efficiency and transfer come from {@link TorqueTier#MEDIUM}. */
public final class TileDistributorMedium extends TileDistributor {

    public TileDistributorMedium(BlockPos pos, BlockState state) {
        super(TorqueTier.MEDIUM, pos, state);
    }

}
