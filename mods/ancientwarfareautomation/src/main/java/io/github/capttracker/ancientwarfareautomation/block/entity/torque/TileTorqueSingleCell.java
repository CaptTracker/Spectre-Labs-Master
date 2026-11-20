/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/torque/TileTorqueSingleCell.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.torque;

import io.github.capttracker.ancientwarfare.api.ITorque.TorqueCell;
import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/*
 * base template class that includes a single torque cell and rotation synching
 *
 * @author Shadowmage
 */
public abstract class TileTorqueSingleCell extends TileTorqueBase {

    private static final String CLIENT_ENERGY_TAG = "clientEnergy";
    private static final String TORQUE_ENERGY_TAG = "torqueEnergy";
    protected TorqueCell torqueCell;

    /*
     * client side this == 0.0 -> 100, as a whole number percent of max rotation value
     */
    protected double clientEnergyState;

    /*
     * server side this == 0 -> 100 (integer percent)
     * client side this == 0 -> 100 (integer percent)
     */
    protected int clientDestEnergyState;

    /*
     * used client side for rendering
     */
    protected double rotation;
    protected double lastRotationDiff;

    protected TileTorqueSingleCell(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public void update() {
        if (!level.isClientSide) {
            serverNetworkUpdate();
            torqueIn = torqueCell.getEnergy() - prevEnergy;
            torqueLoss = applyPowerDrain(torqueCell);
            torqueOut = transferPowerTo(getPrimaryFacing());
            prevEnergy = torqueCell.getEnergy();
        } else {
            clientNetworkUpdate();
            updateRotation();
        }
    }

    protected double applyPowerLoss() {
        return applyPowerDrain(torqueCell);
    }

    @Override
    protected void serverNetworkSynch() {
        int percent = (int) (torqueCell.getPercentFull() * 100.d);
        int percent2 = (int) ((torqueOut / torqueCell.getMaxOutput()) * 100.d);
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
        if (!epsilonEquals(clientEnergyState, clientDestEnergyState)) {
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
        if (side == getPrimaryFacing()) {
            clientDestEnergyState = value;
            networkUpdateTicks = AWAutomationStatics.energyMinNetworkUpdateFrequency;
        }
    }

    @Override
    public double getMaxTorque(@Nullable Direction from) {
        return torqueCell.getMaxEnergy();
    }

    @Override
    public double getTorqueStored(@Nullable Direction from) {
        return torqueCell.getEnergy();
    }

    @Override
    public double addTorque(@Nullable Direction from, double energy) {
        return torqueCell.addEnergy(energy);
    }

    @Override
    public double drainTorque(Direction from, double energy) {
        return torqueCell.drainEnergy(energy);
    }

    @Override
    public double getMaxTorqueOutput(Direction from) {
        return canOutputTorque(from) ? torqueCell.getMaxTickOutput() : 0;
    }

    @Override
    public double getMaxTorqueInput(@Nullable Direction from) {
        return canInputTorque(from) ? torqueCell.getMaxTickInput() : 0;
    }

    @Override
    public boolean useOutputRotation(@Nullable Direction from) {
        return true;
    }

    @Override
    protected double getTotalTorque() {
        return torqueCell.getEnergy();
    }

    @Override
    public boolean canOutputTorque(Direction towards) {
        return towards == getPrimaryFacing();
    }

    @Override
    public boolean canInputTorque(@Nullable Direction from) {
        return from == getPrimaryFacing().getOpposite();
    }

    @Override
    public float getClientOutputRotation(Direction from, float delta) {
        return from == getPrimaryFacing() ? getRenderRotation(rotation, lastRotationDiff, delta) : 0;
    }

    /** The whole number percent (0 to 100) the client animation is easing towards, for renderers that want the speed. */
    public double getClientEnergyState() {
        return clientEnergyState;
    }

    @Override
    protected void handleUpdateNBT(CompoundTag tag) {
        super.handleUpdateNBT(tag);
        clientDestEnergyState = tag.getInt(CLIENT_ENERGY_TAG);
    }

    @Override
    protected void writeUpdateNBT(CompoundTag tag) {
        super.writeUpdateNBT(tag);
        tag.putInt(CLIENT_ENERGY_TAG, clientDestEnergyState);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        torqueCell.setEnergy(tag.getDouble(TORQUE_ENERGY_TAG));
        clientDestEnergyState = tag.getInt(CLIENT_ENERGY_TAG);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble(TORQUE_ENERGY_TAG, torqueCell.getEnergy());
        tag.putInt(CLIENT_ENERGY_TAG, clientDestEnergyState);
    }
}
