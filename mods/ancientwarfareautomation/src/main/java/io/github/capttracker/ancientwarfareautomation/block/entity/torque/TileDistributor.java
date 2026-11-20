/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/torque/TileDistributor.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.torque;

import io.github.capttracker.ancientwarfare.api.ITorque.TorqueCell;
import io.github.capttracker.ancientwarfareautomation.block.torque.TorqueTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * One input (the back of the block), five outputs. The input is split among the connected outputs by their free room.
 */
public abstract class TileDistributor extends TileTorqueSidedCell {

    protected TileDistributor(TorqueTier tier, BlockPos pos, BlockState state) {
        super(tier, pos, state);
    }

    @Override
    protected double transferPower() {
        int in = getPrimaryFacing().getOpposite().get3DDataValue();
        double out = 0;
        for (int i = 0; i < DIRECTION_LENGTH; i++) {
            if (i == in) {
                continue;
            }
            out += transferPowerTo(DIRECTIONS[i]);
        }
        return out;
    }

    @Override
    protected void balanceStorage() {
        int in = getPrimaryFacing().getOpposite().get3DDataValue();
        TorqueCell input = storage[in];
        double totalRequested = 0;

        boolean[] connected = getConnections();
        TorqueCell out;
        for (int i = 0; i < storage.length; i++) {
            if (i == in) {
                continue;
            }
            out = storage[i];
            if (!connected[i]) {
                continue;
            }
            totalRequested += out.getMaxEnergy() - out.getEnergy();
        }
        if (totalRequested > 0 && input.getEnergy() > 0) {
            double transfer = Math.min(totalRequested, input.getEnergy());
            double percent = transfer / totalRequested;
            double request, trans;
            double transferred = 0;
            for (int i = 0; i < storage.length; i++) {
                if (i == in) {
                    continue;
                }
                if (!connected[i]) {
                    continue;
                }
                out = storage[i];
                request = out.getMaxEnergy() - out.getEnergy();
                trans = request * percent;
                transferred += trans;
                out.setEnergy(out.getEnergy() + trans);
            }
            input.setEnergy(input.getEnergy() - transferred);
        }
    }

    @Override
    public boolean canInputTorque(@Nullable Direction from) {
        return from == getPrimaryFacing().getOpposite();
    }

    @Override
    public boolean canOutputTorque(Direction from) {
        return from != getPrimaryFacing().getOpposite();
    }

}
