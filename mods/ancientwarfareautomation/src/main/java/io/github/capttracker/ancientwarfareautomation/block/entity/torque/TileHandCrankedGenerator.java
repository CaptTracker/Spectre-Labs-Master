/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/torque/TileHandCrankedGenerator.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.torque;

import io.github.capttracker.ancientwarfare.api.ITorque.TorqueCell;
import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.api.IWorker;
import io.github.capttracker.ancientwarfare.config.AWCoreStatics;
import io.github.capttracker.ancientwarfare.owner.IOwnable;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfare.upgrade.WorksiteUpgrade;
import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.Team;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Set;

/**
 * A generator that a player (hammer in work mode) or an NPC worker cranks. The cranking fills the input cell (in 32 per
 * call, capacity 150); every tick at most 2 moves on to the output cell (out 32, capacity 32), which pushes to the
 * neighbour in front. Torque can be added from above (the crank) but never through conduits.
 */
public class TileHandCrankedGenerator extends TileTorqueSingleCell implements IWorkSite, IOwnable {

    private Owner owner = Owner.EMPTY;
    private final TorqueCell inputCell;

    /*
     * client side this == 0.0 -> 100.0 (integer percent)
     */
    private double clientInputEnergy;

    /*
     * client side this == 0 -> 100.0 (integer percent)
     */
    private int clientInputDestEnergy;

    /*
     * used client side for rendering
     */
    private double inputRotation;
    private double lastInputRotationDiff;

    public TileHandCrankedGenerator(BlockPos pos, BlockState state) {
        super(pos, state);
        double eff = AWAutomationStatics.low_efficiency_factor;
        torqueCell = new TorqueCell(0, 32, 32, eff);
        inputCell = new TorqueCell(32, 0, 150, eff);
    }

    @Override
    public void update() {
        if (!level.isClientSide) {
            serverNetworkUpdate();
            torqueIn = torqueCell.getEnergy() - prevEnergy;
            balancePower();
            torqueOut = transferPowerTo(getPrimaryFacing());
            torqueLoss = applyPowerDrain(torqueCell);
            torqueLoss += applyPowerDrain(inputCell);
            prevEnergy = torqueCell.getEnergy();
        } else {
            clientNetworkUpdate();
            updateRotation();
        }
    }

    private void balancePower() {
        double trans = Math.min(2.d, torqueCell.getMaxEnergy() - torqueCell.getEnergy());
        trans = Math.min(trans, inputCell.getEnergy());
        inputCell.setEnergy(inputCell.getEnergy() - trans);
        torqueCell.setEnergy(torqueCell.getEnergy() + trans);
    }

    @Override
    protected void serverNetworkSynch() {
        super.serverNetworkSynch();
        int percent = (int) (inputCell.getPercentFull() * 100d);
        if (percent != clientInputDestEnergy) {
            clientInputDestEnergy = percent;
            sendSideRotation(Direction.UP, percent);
        }
    }

    @Override
    protected void updateRotation() {
        super.updateRotation();
        if (clientInputEnergy > 0) {
            lastInputRotationDiff = -(AWAutomationStatics.low_rpt * clientInputEnergy * 0.01d) * Trig.TORADIANS;
            inputRotation += lastInputRotationDiff;
            inputRotation %= Trig.PI * (float) 2;
        }
    }

    @Override
    protected void clientNetworkUpdate() {
        if (!epsilonEquals(clientEnergyState, clientDestEnergyState) || !epsilonEquals(clientInputEnergy, clientInputDestEnergy)) {
            if (networkUpdateTicks >= 0) {
                clientEnergyState += (clientDestEnergyState - clientEnergyState) / ((double) networkUpdateTicks + 1.d);
                clientInputEnergy += (clientInputDestEnergy - clientInputEnergy) / ((double) networkUpdateTicks + 1.d);
                networkUpdateTicks--;
            } else {
                clientEnergyState = clientDestEnergyState;
                clientInputEnergy = clientInputDestEnergy;
            }
        }
    }

    @Override
    protected void handleClientRotationData(Direction side, int value) {
        super.handleClientRotationData(side, value);
        if (side == Direction.UP) {
            clientInputDestEnergy = value;
        }
    }

    @Override
    public void onBlockBroken(BlockState state) {
        //NOOP
    }

    @Override
    public Set<WorksiteUpgrade> getUpgrades() {
        return EnumSet.noneOf(WorksiteUpgrade.class);
    }

    @Override
    public Set<WorksiteUpgrade> getValidUpgrades() {
        return EnumSet.noneOf(WorksiteUpgrade.class);
    }

    @Override
    public void addUpgrade(WorksiteUpgrade upgrade) {
        // NOOP
    }

    @Override
    public void removeUpgrade(WorksiteUpgrade upgrade) {
        // NOOP
    }

    @Override
    public void onLoad() {
        super.onLoad();
        io.github.capttracker.ancientwarfare.api.WorksiteRegistry.register(this);
    }

    @Override
    public void onChunkUnloaded() {
        io.github.capttracker.ancientwarfare.api.WorksiteRegistry.unregister(this);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        io.github.capttracker.ancientwarfare.api.WorksiteRegistry.unregister(this);
        super.setRemoved();
    }

    @Override
    public boolean hasWork() {
        return inputCell.getEnergy() < inputCell.getMaxEnergy();
    }

    @Override
    public void addEnergyFromWorker(IWorker worker) {
        inputCell.setEnergy(inputCell.getEnergy() + AWCoreStatics.energyPerWorkUnit * worker.getWorkEffectiveness(getWorkType()) * AWAutomationStatics.hand_cranked_generator_output);
    }

    @Override
    public void addEnergyFromPlayer(Player player) {
        inputCell.setEnergy(inputCell.getEnergy() + AWCoreStatics.energyPerWorkUnit * AWAutomationStatics.hand_cranked_generator_output);
    }

    @Override
    public WorkType getWorkType() {
        return WorkType.CRAFTING;
    }

    @Override
    @Nullable
    public Team getTeam() {
        return level == null ? null : level.getScoreboard().getPlayersTeam(owner.getName());
    }

    @Override
    public boolean isOwner(Player player) {
        return owner.isOwnerOrSameTeamOrFriend(player);
    }

    @Override
    public void setOwner(Player player) {
        setOwner(new Owner(player));
    }

    @Override
    public void setOwner(Owner owner) {
        this.owner = owner;
        setChanged();
    }

    @Override
    public Owner getOwner() {
        return owner;
    }

    @Override
    protected void handleUpdateNBT(CompoundTag tag) {
        super.handleUpdateNBT(tag);
        clientInputDestEnergy = tag.getInt("clientInputDestEnergy");
    }

    @Override
    protected void writeUpdateNBT(CompoundTag tag) {
        super.writeUpdateNBT(tag);
        tag.putInt("clientInputDestEnergy", clientInputDestEnergy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = Owner.deserializeFromNBT(tag);
        inputCell.setEnergy(tag.getDouble("inputEnergy"));
        clientInputDestEnergy = tag.getInt("clientInputEnergy");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        owner.serializeToNBT(tag);
        tag.putDouble("inputEnergy", inputCell.getEnergy());
        tag.putInt("clientInputEnergy", clientInputDestEnergy);
    }

    @Override
    public double getMaxTorque(@Nullable Direction from) {
        return inputCell.getMaxEnergy() + torqueCell.getMaxEnergy();
    }

    @Override
    public double getTorqueStored(@Nullable Direction from) {
        return inputCell.getEnergy() + torqueCell.getEnergy();
    }

    @Override
    public double addTorque(@Nullable Direction from, double energy) {
        if (from == getPrimaryFacing()) {
            return 0;
        } else if (from == Direction.UP || from == null) {
            return inputCell.addEnergy(energy);
        }
        return 0;
    }

    @Override
    public double drainTorque(Direction from, double energy) {
        if (from == getPrimaryFacing()) {
            return torqueCell.drainEnergy(energy);
        }
        return 0;
    }

    @Override
    public double getMaxTorqueOutput(Direction from) {
        if (from == getPrimaryFacing()) {
            return torqueCell.getMaxTickOutput();
        }
        return 0;
    }

    @Override
    public double getMaxTorqueInput(@Nullable Direction from) {
        return 0;
    }

    @Override
    public boolean canInputTorque(@Nullable Direction from) {
        return false;
    }

    @Override
    public float getClientOutputRotation(Direction from, float delta) {
        float ret = 0;
        if (from == getPrimaryFacing()) {
            ret = getRenderRotation(rotation, lastRotationDiff, delta);
        } else if (from == Direction.UP) {
            ret = getRenderRotation(inputRotation, lastInputRotationDiff, delta);
        }
        return ret;
    }

    /** Rotation of the crank handle in radians, for the renderer (same as the output rotation of the side up). */
    public float getCrankRotation(float delta) {
        return getRenderRotation(inputRotation, lastInputRotationDiff, delta);
    }

    @Override
    public boolean useOutputRotation(@Nullable Direction from) {
        return true;
    }

    @Override
    protected double getTotalTorque() {
        return inputCell.getEnergy() + torqueCell.getEnergy();
    }

}
