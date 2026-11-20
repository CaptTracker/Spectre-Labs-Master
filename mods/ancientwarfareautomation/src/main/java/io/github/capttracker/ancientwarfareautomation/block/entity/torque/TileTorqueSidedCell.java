/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/torque/TileTorqueSidedCell.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.torque;

import io.github.capttracker.ancientwarfare.api.ITorque.ITorqueTile;
import io.github.capttracker.ancientwarfare.api.ITorque.SidedTorqueCell;
import io.github.capttracker.ancientwarfare.api.ITorque.TorqueCell;
import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfareautomation.block.torque.TorqueTier;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * A block entity with one torque cell per side (junction, distributor). The energy of a side stays with the world
 * side when the block is turned (see {@link SidedTorqueCell}). The tier decides cell size, transfer and efficiency;
 * in 1.12.2 that came from the abstract {@code getMaxTransfer()} and {@code getEfficiency()} of the tier subclasses.
 */
public abstract class TileTorqueSidedCell extends TileTorqueBase {

    private static final String CLIENT_ENERGY_TAG = "clientEnergy";
    private boolean[] connections = null;
    final SidedTorqueCell[] storage = new SidedTorqueCell[DIRECTION_LENGTH];
    private final TorqueTier tier;

    /*
     * client side this == 0.0 -> 100.0
     */
    private double clientEnergyState;

    /*
     * server side this == 0 -> 100 (integer percent)
     * client side this == 0 -> 100 (integer percent)
     */
    private int clientDestEnergyState;

    /*
     * used client side for rendering
     */
    private double rotation;
    private double lastRotationDiff;

    protected TileTorqueSidedCell(TorqueTier tier, BlockPos pos, BlockState state) {
        super(pos, state);
        this.tier = tier;
        double max = tier.getMaxTransfer();
        double eff = tier.getEfficiency();
        for (int i = 0; i < storage.length; i++) {
            storage[i] = new SidedTorqueCell(max, max, max, eff, DIRECTIONS[i], this);
        }
    }

    public final TorqueTier getTier() {
        return tier;
    }

    @Override
    public void update() {
        if (!level.isClientSide) {
            serverNetworkUpdate();
            torqueIn = getTotalTorque() - prevEnergy;
            balanceStorage();
            torqueLoss = applyPowerLoss();
            torqueOut = transferPower();
            prevEnergy = getTotalTorque();
        } else {
            clientNetworkUpdate();
            updateRotation();
        }
    }

    private double applyPowerLoss() {
        double loss = 0;
        for (SidedTorqueCell aStorage : storage) {
            loss += applyPowerDrain(aStorage);
        }
        return loss;
    }

    protected double transferPower() {
        return transferPowerTo(getPrimaryFacing());
    }

    protected void balanceStorage() {
        int face = getPrimaryFacing().get3DDataValue();
        TorqueCell out = storage[face];
        double total = 0;
        TorqueCell in;
        for (int i = 0; i < storage.length; i++) {
            if (i == face) {
                continue;
            }
            total += storage[i].getEnergy();
        }
        if (total > 0) {
            double transfer = Math.min(total, out.getMaxEnergy() - out.getEnergy());
            double percent = transfer / total;
            transfer = 0;
            double fromEach;
            for (int i = 0; i < storage.length; i++) {
                if (i == face) {
                    continue;
                }
                in = storage[i];
                fromEach = in.getEnergy() * percent;
                transfer += fromEach;
                in.setEnergy(in.getEnergy() - fromEach);
            }
            out.setEnergy(out.getEnergy() + transfer);
        }
    }

    @Override
    protected void serverNetworkSynch() {
        int face = getPrimaryFacing().get3DDataValue();
        int percent = (int) (storage[face].getPercentFull() * 100.d);
        int percent2 = (int) ((torqueOut / storage[face].getMaxOutput()) * 100.d);
        percent = Math.max(percent, percent2);
        if (percent != clientDestEnergyState) {
            clientDestEnergyState = percent;
            sendSideRotation(getPrimaryFacing(), percent);
        }
    }

    @Override
    protected void updateRotation() {
        if (clientEnergyState > 0) {
            lastRotationDiff = -(AWAutomationStatics.low_rpt * clientEnergyState * 0.01d) * Trig.TORADIANS;
            rotation += lastRotationDiff;
            rotation %= Trig.PI * 2;
        }
    }

    @Override
    protected void clientNetworkUpdate() {
        if (clientEnergyState != clientDestEnergyState) {
            if (networkUpdateTicks > 0) {
                clientEnergyState += (clientDestEnergyState - clientEnergyState) / ((double) networkUpdateTicks);
                networkUpdateTicks--;
            } else {
                clientEnergyState = clientDestEnergyState;
            }
        }
    }

    @Override
    protected void handleClientRotationData(Direction side, int value) {
        clientDestEnergyState = value;
        this.networkUpdateTicks = AWAutomationStatics.energyMinNetworkUpdateFrequency;
    }

    @Override
    public boolean canInputTorque(@Nullable Direction from) {
        return from != getPrimaryFacing();
    }

    @Override
    public boolean canOutputTorque(Direction towards) {
        return towards == getPrimaryFacing();
    }

    /**
     * Which of the six sides (index = direction ordinal: down, up, north, south, west, east) is connected to a
     * neighbour that takes or gives torque to it, or to an FE block. The conduit shape and the renderer use this.
     */
    public boolean[] getConnections() {
        if (connections == null) {
            buildConnections();
        }
        return connections;
    }

    public boolean isConnected(Direction side) {
        return getConnections()[side.get3DDataValue()];
    }

    @Override
    public void onNeighborTileChanged() {
        super.onNeighborTileChanged();
        connections = null;
    }

    @Override
    protected void onNeighborCacheInvalidated() {
        super.onNeighborCacheInvalidated();
        connections = null;
    }

    private void buildConnections() {
        boolean[] updatedConnections = new boolean[DIRECTION_LENGTH];
        ITorqueTile[] cache = getTorqueCache();
        Direction dir;
        for (int i = 0; i < cache.length; i++) {
            dir = DIRECTIONS[i];
            if (cache[i] != null) {
                updatedConnections[i] = (cache[i].canInputTorque(dir.getOpposite()) && canOutputTorque(dir)) || (cache[i].canOutputTorque(dir.getOpposite()) && canInputTorque(dir));
            } else if (hasNeighbourEnergy(dir)) {
                //an FE block (and not a torque tile): the conduit attaches to it visually
                updatedConnections[i] = true;
            }
        }
        this.connections = updatedConnections;
    }

    @Override
    public double getMaxTorque(@Nullable Direction from) {
        return from == null ? 0 : storage[from.get3DDataValue()].getMaxEnergy();
    }

    @Override
    public double getTorqueStored(@Nullable Direction from) {
        if (from == null) {
            return 0D; // some mods pass null into energy compat so let's return 0 in that case
        }
        return storage[from.get3DDataValue()].getEnergy();
    }

    @Override
    public double addTorque(@Nullable Direction from, double energy) {
        return from == null ? 0 : storage[from.get3DDataValue()].addEnergy(energy);
    }

    @Override
    public double drainTorque(Direction from, double energy) {
        return storage[from.get3DDataValue()].drainEnergy(energy);
    }

    @Override
    public double getMaxTorqueOutput(Direction from) {
        return storage[from.get3DDataValue()].getMaxTickOutput();
    }

    @Override
    public double getMaxTorqueInput(@Nullable Direction from) {
        return from == null ? 0 : storage[from.get3DDataValue()].getMaxTickInput();
    }

    @Override
    public boolean useOutputRotation(@Nullable Direction from) {
        return true;
    }

    @Override
    public float getClientOutputRotation(Direction from, float delta) {
        return getRenderRotation(rotation, lastRotationDiff, delta);
    }

    /** The whole number percent (0 to 100) the client animation is easing towards, for renderers that want the speed. */
    public double getClientEnergyState() {
        return clientEnergyState;
    }

    @Override
    protected void writeUpdateNBT(CompoundTag tag) {
        super.writeUpdateNBT(tag);
        tag.putInt(CLIENT_ENERGY_TAG, clientDestEnergyState);
    }

    @Override
    protected void handleUpdateNBT(CompoundTag tag) {
        super.handleUpdateNBT(tag);
        clientDestEnergyState = tag.getInt(CLIENT_ENERGY_TAG);
        connections = null; //clear connections on update
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ListTag list = tag.getList("energyList", Tag.TAG_COMPOUND);
        for (int i = 0; i < storage.length && i < list.size(); i++) {
            storage[i].readFromNBT(list.getCompound(i));
        }
        clientDestEnergyState = tag.getInt(CLIENT_ENERGY_TAG);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (SidedTorqueCell aStorage : storage) {
            list.add(aStorage.writeToNBT(new CompoundTag()));
        }
        tag.put("energyList", list);
        tag.putInt(CLIENT_ENERGY_TAG, clientDestEnergyState);
    }

    @Override
    protected double getTotalTorque() {
        double d = 0;
        Direction dir;
        for (int i = 0; i < storage.length; i++) {
            dir = DIRECTIONS[i];
            if (canInputTorque(dir) || canOutputTorque(dir)) {
                d += storage[i].getEnergy();
            }
        }
        return d;
    }

}
