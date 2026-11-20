/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the Redstone Flux methods of automation/tile/worksite/TileWorksiteBase (and of
 * TileTorqueBase) and automation/proxy/RFProxy + RFProxyActual.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.ITorque.ITorqueTile;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

/**
 * The Forge Energy ({@code Capabilities.EnergyStorage.BLOCK}) face of a torque tile, replacing the Redstone Flux
 * {@code IEnergyReceiver}/{@code IEnergyProvider} methods the 1.12.2 tiles carried. It is receive-only: energy
 * goes in at {@link AWAutomationStatics#rfToTorque} torque per unit (default 10 FE per torque unit), nothing can be
 * extracted. Used for one side of one tile, the tile's own rules ({@code canInputTorque}) decide what is accepted.
 *
 * <p>{@link #transferPower} is the output direction ({@code RFProxy.transferPower}), for generators that push
 * their torque into a neighbouring FE receiver.
 *
 * <p>Wiring: {@code event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> TorqueEnergyStorage.forTile(be, side))}
 * (the worksites are registered by {@code AwAutomationBase}; the torque slice does the same for its tiles).
 */
public final class TorqueEnergyStorage implements IEnergyStorage {
    private final ITorqueTile tile;
    @Nullable
    private final Direction side;

    private TorqueEnergyStorage(ITorqueTile tile, @Nullable Direction side) {
        this.tile = tile;
        this.side = side;
    }

    /** The storage for the side, or null when energy conversion is switched off ({@code rf_energy_conversion_factor <= 0}). */
    @Nullable
    public static TorqueEnergyStorage forTile(ITorqueTile tile, @Nullable Direction side) {
        return AWAutomationStatics.torqueToRf > 0 ? new TorqueEnergyStorage(tile, side) : null;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (!tile.canInputTorque(side)) {
            return 0;
        }
        if (simulate) {
            return Math.min(toReceive, (int) (AWAutomationStatics.torqueToRf * tile.getMaxTorqueInput(side)));
        }
        return (int) (AWAutomationStatics.torqueToRf * tile.addTorque(side, (double) toReceive * AWAutomationStatics.rfToTorque));
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return (int) (tile.getTorqueStored(side) * AWAutomationStatics.torqueToRf);
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) (tile.getMaxTorque(side) * AWAutomationStatics.torqueToRf);
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return tile.canInputTorque(side);
    }

    /**
     * Pushes torque out of the generator's side {@code from} into an FE receiver (looked up on the neighbour's
     * opposite side by the caller).
     *
     * @return the torque energy taken from the generator
     */
    public static double transferPower(ITorqueTile generator, Direction from, IEnergyStorage target) {
        if (AWAutomationStatics.torqueToRf <= 0 || !target.canReceive()) {
            return 0;
        }
        int accepted = target.receiveEnergy((int) (generator.getMaxTorqueOutput(from) * AWAutomationStatics.torqueToRf), false);
        return generator.drainTorque(from, accepted * AWAutomationStatics.rfToTorque);
    }
}
