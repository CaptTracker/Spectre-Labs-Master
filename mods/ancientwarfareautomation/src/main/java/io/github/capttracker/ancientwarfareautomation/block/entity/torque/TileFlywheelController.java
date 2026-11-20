/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/torque/TileFlywheelController.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.torque;

import io.github.capttracker.ancientwarfare.api.ITorque.TorqueCell;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.multiblock.TileFlywheelStorage;
import io.github.capttracker.ancientwarfareautomation.block.torque.TorqueTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The input/output block on top of a flywheel stack. Input comes in at the back, output leaves at the front, and the
 * stack below (found through the storage block directly under the controller) is the buffer. A redstone signal
 * switches the output off (redstone is the output switch).
 */
public abstract class TileFlywheelController extends TileTorqueSingleCell {
    private static final String POWERED_TAG = "powered";
    /** Block entity event type for the powered flag (the animation channel uses the side numbers 0 to 5). */
    private static final int POWERED_EVENT = 7;
    private boolean powered;

    private final TorqueCell inputCell;
    private final TorqueTier tier;

    protected TileFlywheelController(TorqueTier tier, BlockPos pos, BlockState state) {
        super(pos, state);
        this.tier = tier;
        double max = tier.getMaxTransfer();
        double eff = tier.getEfficiency();
        inputCell = new TorqueCell(max, max, max, eff);
        torqueCell = new TorqueCell(max, max, max, eff);
    }

    public final TorqueTier getTier() {
        return tier;
    }

    @Override
    public void update() {
        if (!level.isClientSide) {
            serverNetworkUpdate();
            torqueIn = torqueCell.getEnergy() - prevEnergy;
            torqueLoss = applyPowerDrain(torqueCell);
            torqueLoss += applyPowerDrain(inputCell);
            torqueLoss += applyDrainToStorage();
            torqueOut = transferPowerTo(getPrimaryFacing());
            balancePower();
            prevEnergy = torqueCell.getEnergy();
        } else {
            clientNetworkUpdate();
            updateRotation();
        }
    }

    private double applyDrainToStorage() {
        TileFlywheelStorage storage = getControlledFlywheel();
        if (storage == null) {
            return 0;
        }
        return storage.torqueLoss;
    }

    /*
     * fill output from input
     * fill output from storage
     * fill storage from input
     */
    private void balancePower() {
        TileFlywheelStorage storage = getControlledFlywheel();
        double in = inputCell.getEnergy();
        double out = torqueCell.getEnergy();
        double outputGap = torqueCell.getMaxEnergy() - out;
        double addOutput = Math.min(in, outputGap);
        in -= addOutput;
        out += addOutput;
        if (storage != null) {
            double store = storage.storedEnergy;
            double storeToTransfer = Math.min(store, torqueCell.getMaxEnergy() - out);
            store -= storeToTransfer;
            out += storeToTransfer;

            double addToStore = Math.min(in, storage.maxEnergyStored - store);
            in -= addToStore;
            store += addToStore;
            storage.storedEnergy = store;
            //the original adds the storage loss a second time here (it is part of applyDrainToStorage already);
            //it only feeds the right-click read-out, so it is kept as it was
            torqueLoss += storage.torqueLoss;
        }
        torqueCell.setEnergy(out);
        inputCell.setEnergy(in);
    }

    @Override
    protected void updateRotation() {
        if (!powered) {
            super.updateRotation();
        }
    }

    /** The control block of the flywheel stack directly below, or null if there is none or the stack is not valid. */
    @Nullable
    private TileFlywheelStorage getControlledFlywheel() {
        BlockPos belowPos = worldPosition.below();
        return WorldTools.getTile(level, belowPos, TileFlywheelStorage.class).map(t -> {
                    if (t.controllerPos != null) {
                        BlockPos nextControllerPos = t.controllerPos;
                        return WorldTools.getTile(level, nextControllerPos, TileFlywheelStorage.class).orElse(null);
                    }
                    return null;
                }
        ).orElse(null);
    }

    /** Rotation of the flywheel stack below, in radians, for the renderer. */
    public float getFlywheelRotation(float delta) {
        TileFlywheelStorage storage = getControlledFlywheel();
        return storage == null ? 0 : getRenderRotation(storage.rotation, storage.lastRotationDiff, delta);
    }

    private double getFlywheelEnergy() {
        TileFlywheelStorage storage = getControlledFlywheel();
        return storage == null ? 0 : storage.storedEnergy;
    }

    @Override
    protected double getTotalTorque() {
        return inputCell.getEnergy() + torqueCell.getEnergy() + getFlywheelEnergy();
    }

    /** True while a redstone signal switches the output off. */
    public boolean isPowered() {
        return powered;
    }

    /** Re-reads the redstone signal; also called by the block when it is placed. */
    @Override
    public void onNeighborTileChanged() {
        super.onNeighborTileChanged();
        if (level != null && !level.isClientSide) {
            boolean p = level.getDirectSignalTo(worldPosition) > 0;
            if (p != powered) {
                powered = p;
                setChanged();
                sendDataToClient(POWERED_EVENT, powered ? 1 : 0);
            }
        }
    }

    @Override
    protected void handleClientEvent(int a, int b) {
        if (a == POWERED_EVENT) {
            powered = b == 1;
        }
        super.handleClientEvent(a, b);
    }

    @Override
    public double getMaxTorqueOutput(Direction from) {
        if (powered) {
            return 0;
        }
        return torqueCell.getMaxTickOutput();
    }

    @Override
    public double getMaxTorque(@Nullable Direction from) {
        TorqueCell cell = getCell(from);
        return cell == null ? 0 : cell.getMaxEnergy();
    }

    @Override
    public double getTorqueStored(@Nullable Direction from) {
        TorqueCell cell = getCell(from);
        return cell == null ? 0 : cell.getEnergy();
    }

    @Override
    public double addTorque(@Nullable Direction from, double energy) {
        TorqueCell cell = getCell(from);
        return cell == null ? 0 : cell.addEnergy(energy);
    }

    @Override
    public double drainTorque(@Nullable Direction from, double energy) {
        TorqueCell cell = getCell(from);
        return cell == null ? 0 : cell.drainEnergy(energy);
    }

    @Override
    public double getMaxTorqueInput(@Nullable Direction from) {
        TorqueCell cell = getCell(from);
        return cell == null ? 0 : cell.getMaxTickInput();
    }

    @Nullable
    private TorqueCell getCell(@Nullable Direction from) {
        Direction facing = getPrimaryFacing();
        if (from == facing) {
            return torqueCell;
        } else if (from == facing.getOpposite()) {
            return inputCell;
        }
        return null;
    }

    //************************************** NBT / DATA PACKET ***************************************//

    @Override
    protected void handleUpdateNBT(CompoundTag tag) {
        super.handleUpdateNBT(tag);
        powered = tag.getBoolean(POWERED_TAG);
    }

    @Override
    protected void writeUpdateNBT(CompoundTag tag) {
        super.writeUpdateNBT(tag);
        tag.putBoolean(POWERED_TAG, powered);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(POWERED_TAG, powered);
        tag.putDouble("torqueEnergyIn", inputCell.getEnergy());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        powered = tag.getBoolean(POWERED_TAG);
        inputCell.setEnergy(tag.getDouble("torqueEnergyIn"));
    }

}
