/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/torque/TileWindmillController.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.torque;

import io.github.capttracker.ancientwarfare.api.ITorque.TorqueCell;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.multiblock.TileWindmillBlade;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The output block of a windmill. It must sit directly behind the control (centre) blade block; every tick it takes the
 * energy the control block produced and zeroes it there. Output is capped by this cell (32 per tick).
 */
public class TileWindmillController extends TileTorqueSingleCell {

    public TileWindmillController(BlockPos pos, BlockState state) {
        super(pos, state);
        double max = AWAutomationStatics.low_transfer_max;
        torqueCell = new TorqueCell(max, max, max, AWAutomationStatics.low_efficiency_factor);
    }

    @Override
    public void update() {
        super.update();
        if (!level.isClientSide) {
            TileWindmillBlade blade = getControlledBlade();
            if (blade != null && blade.getEnergy() > 0) {
                double d = blade.getEnergy();
                blade.setEnergy(0);
                torqueCell.setEnergy(torqueCell.getEnergy() + d);
            }
        }
    }

    @Nullable
    private TileWindmillBlade getControlledBlade() {
        BlockPos behind = worldPosition.relative(getPrimaryFacing().getOpposite());
        if (level.isLoaded(behind)) {
            return WorldTools.getTile(level, behind, TileWindmillBlade.class).orElse(null);
        }
        return null;
    }

    @Override
    public boolean canInputTorque(@Nullable Direction from) {
        return false;
    }

}
