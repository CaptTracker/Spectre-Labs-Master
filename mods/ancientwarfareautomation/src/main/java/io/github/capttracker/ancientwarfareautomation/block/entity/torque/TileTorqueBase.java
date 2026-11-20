/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/torque/TileTorqueBase (with the RFProxy replaced by the
 * NeoForge energy capability).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.torque;

import io.github.capttracker.ancientwarfare.api.AwCapabilities;
import io.github.capttracker.ancientwarfare.api.IInteractableTile;
import io.github.capttracker.ancientwarfare.api.ITorque.ITorqueTile;
import io.github.capttracker.ancientwarfare.api.ITorque.TorqueCell;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.IRotatableTile;
import io.github.capttracker.ancientwarfare.block.entity.UpdatableBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.torque.BlockTorqueBase;
import io.github.capttracker.ancientwarfareautomation.block.torque.IBlockEntityTypeProvider;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

/**
 * Base of every torque block entity: neighbour cache, push-style transfer to the neighbour in front, statistics for
 * the right-click read-out, the client animation channel, and the energy (FE) bridge.
 *
 * <p>Differences from the 1.12.2 tile:
 * <ul>
 *   <li>The facing ({@code orientation}) lives in the block state; {@link #getPrimaryFacing()} reads it and
 *   {@link #setPrimaryFacing} writes it. The cache is cleared when the state changes ({@link #setBlockState}).</li>
 *   <li>The cofh {@code IEnergyReceiver} methods are an {@link IEnergyStorage} capability ({@link #getEnergyStorage}),
 *   and sending uses the energy capability of the neighbour. 1 torque is {@code torqueToRf} FE, as before.</li>
 *   <li>Neighbours are found with the torque capability or by {@code instanceof ITorqueTile}; the cache also drops
 *   block entities that were removed (chunk unload) so it never keeps a stale neighbour.</li>
 *   <li>The animation values reach the client as block entity update packets ({@link TorqueNetwork}).</li>
 * </ul>
 *
 * <p>A renderer reads {@link #getClientOutputRotation}, {@link #getPrimaryFacing()} and the getters of the subclasses
 * (connections, previous/next shaft, powered, valid setup, ...); nothing here depends on rendering.
 */
public abstract class TileTorqueBase extends UpdatableBlockEntity implements ITorqueTile, IInteractableTile, IRotatableTile {

    public static final int DIRECTION_LENGTH = Direction.values().length;
    /** Same order as the 1.12.2 EnumFacing values: down, up, north, south, west, east. */
    protected static final Direction[] DIRECTIONS = Direction.values();

    /*
     * used by server to limit packet sending<br>
     * used by client for lerp-ticks for lerping between power states
     */
    protected int networkUpdateTicks;

    private ITorqueTile[] torqueCache;
    private BlockCapabilityCache<IEnergyStorage, Direction>[] energyCaches;
    private EnergyAdapter[] energyAdapters;

    /*
     * helper vars to be used by tiles during updating, to cache in/out/loss values<br>
     * IMPORTANT: should NOT be relied upon for calculations, only for use for display purposes<br>
     * E.G. A tile may choose to -not- update these vars.<br>
     * However, best effort should be made to update these vars accurately.<br><br>
     * Generated energy should be counted as 'in'<br>
     * Any directly output energy should be counted as 'out'<br>
     * Only direct power loss from transmission efficiency or per-tick loss should be counted for 'loss'
     */
    protected double torqueIn;
    protected double torqueOut;
    protected double torqueLoss;
    protected double prevEnergy;

    protected TileTorqueBase(BlockPos pos, BlockState state) {
        super(IBlockEntityTypeProvider.typeOf(state), pos, state);
    }

    // ************************************** TICK ***************************************//

    /** Called every tick on both sides by the block's ticker. Server: energy logic. Client: animation. */
    public abstract void update();

    // ************************************** ENERGY (FE) CAPABILITY ***************************************//

    /**
     * The energy capability of the given side, or null if the side neither takes nor gives torque (or FE is
     * switched off with a non-positive conversion factor). Replaces cofh {@code canConnectEnergy}, {@code receiveEnergy},
     * {@code getEnergyStored} and {@code getMaxEnergyStored}. The block entity only receives; it never lets others extract.
     */
    @Nullable
    public final IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        if (side == null || AWAutomationStatics.torqueToRf <= 0 || !(canOutputTorque(side) || canInputTorque(side))) {
            return null;
        }
        if (energyAdapters == null) {
            energyAdapters = new EnergyAdapter[DIRECTION_LENGTH];
        }
        int index = side.get3DDataValue();
        if (energyAdapters[index] == null) {
            energyAdapters[index] = new EnergyAdapter(side);
        }
        return energyAdapters[index];
    }

    private final class EnergyAdapter implements IEnergyStorage {
        private final Direction side;

        private EnergyAdapter(Direction side) {
            this.side = side;
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            if (!canInputTorque(side)) {
                return 0;
            }
            if (simulate) {
                return Math.min(toReceive, (int) (AWAutomationStatics.torqueToRf * getMaxTorqueInput(side)));
            }
            return (int) (AWAutomationStatics.torqueToRf * addTorque(side, (double) toReceive * AWAutomationStatics.rfToTorque));
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) (getTorqueStored(side) * AWAutomationStatics.torqueToRf);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) (getMaxTorque(side) * AWAutomationStatics.torqueToRf);
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return canInputTorque(side);
        }
    }

    // ************************************** NEIGHBOR CACHE UPDATING ***************************************//

    public final ITorqueTile[] getTorqueCache() {
        if (torqueCache == null) {
            buildTorqueCache();
        }
        return torqueCache;
    }

    private void buildTorqueCache() {
        if (level == null) {
            throw new IllegalStateException("Attempt to build neighbor cache on null level!!");
        }
        ITorqueTile[] cache = new ITorqueTile[DIRECTION_LENGTH];
        for (Direction dir : DIRECTIONS) {
            if (!canOutputTorque(dir) && !canInputTorque(dir)) {
                continue;
            }
            BlockPos offsetPos = worldPosition.relative(dir);
            if (!level.isLoaded(offsetPos)) {
                continue;
            }
            cache[dir.get3DDataValue()] = findTorqueTile(offsetPos, dir.getOpposite());
        }
        this.torqueCache = cache;
    }

    @Nullable
    private ITorqueTile findTorqueTile(BlockPos pos, Direction sideOfNeighbour) {
        BlockEntity neighbour = level.getBlockEntity(pos);
        if (neighbour instanceof ITorqueTile tile) {
            return tile;
        }
        if (neighbour != null) {
            return level.getCapability(AwCapabilities.TORQUE, pos, sideOfNeighbour);
        }
        return null;
    }

    /** The FE storage of the neighbour on that side, server side only; null if there is none or the chunk is not loaded. */
    @Nullable
    @SuppressWarnings("unchecked")
    private IEnergyStorage getNeighbourEnergy(Direction dir) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        BlockPos neighbourPos = worldPosition.relative(dir);
        if (!serverLevel.isLoaded(neighbourPos)) {
            return null;
        }
        if (energyCaches == null) {
            energyCaches = new BlockCapabilityCache[DIRECTION_LENGTH];
        }
        int index = dir.get3DDataValue();
        if (energyCaches[index] == null) {
            energyCaches[index] = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, serverLevel, neighbourPos, dir.getOpposite());
        }
        return energyCaches[index].getCapability();
    }

    /** Whether the block on that side has an energy capability on the side facing us (client or server). Used by conduits for their visual connections. */
    protected final boolean hasNeighbourEnergy(Direction dir) {
        BlockPos neighbourPos = worldPosition.relative(dir);
        return level != null && level.isLoaded(neighbourPos)
                && level.getCapability(Capabilities.EnergyStorage.BLOCK, neighbourPos, dir.getOpposite()) != null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        invalidateTorqueCache();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        invalidateTorqueCache();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        energyCaches = null;
        energyAdapters = null;
        invalidateTorqueCache();
    }

    /** The block state changed (facing turned by a hammer, for example): what connects where is different now. */
    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState blockState) {
        super.setBlockState(blockState);
        energyAdapters = null;
        invalidateTorqueCache();
        invalidateCapabilities();
    }

    /** Called by the block when a neighbouring block changed. */
    public void onNeighborTileChanged() {
        invalidateTorqueCache();
    }

    private void invalidateTorqueCache() {
        torqueCache = null;
        onNeighborCacheInvalidated();
    }

    protected void onNeighborCacheInvalidated() {

    }

    // ************************************** generic stuff ***************************************//

    @Override
    public final void setPrimaryFacing(Direction d) {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        BlockState rotated = BlockTorqueBase.withFacing(state, d);
        if (rotated != state) {
            level.setBlock(worldPosition, rotated, Block.UPDATE_ALL);//BlockEntity.setBlockState clears the caches
        } else {
            invalidateTorqueCache();
        }
    }

    /** The side the block faces; this is the output side of most torque blocks. Read from the block state. */
    @Override
    public final Direction getPrimaryFacing() {
        return BlockTorqueBase.getFacing(getBlockState());
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        ItemStack stack = hand == null ? ItemStack.EMPTY : player.getItemInHand(hand);
        if (stack.isEmpty()) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.translatable("guistrings.automation.torque.values", String.format("%.2f", getTotalTorque()), String.format("%.2f", getTorqueIn()), String.format("%.2f", getTorqueOut()), String.format("%.2f", getTorqueLoss())));
            }
            return true;
        }
        return false;
    }

    //************************************** Utility Methods ***************************************//

    protected void updateRotation() {
        throw new UnsupportedOperationException();
    }

    protected void clientNetworkUpdate() {
        throw new UnsupportedOperationException();
    }

    protected void serverNetworkSynch() {
        throw new UnsupportedOperationException();
    }

    protected abstract void handleClientRotationData(Direction side, int value);

    /*
     * @return the TOTAL amount stored in the entire tile (not just one side), used by on-right-click functionality
     */
    protected abstract double getTotalTorque();

    /*
     * @return the total output of torque for the tick
     */
    private double getTorqueOut() {
        return torqueOut;
    }

    /*
     * @return the total input of torque for the tick
     */
    private double getTorqueIn() {
        return torqueIn;
    }

    /*
     * @return the total torque lost (destroyed, gone completely) for the tick
     */
    private double getTorqueLoss() {
        return torqueLoss;
    }

    protected final void serverNetworkUpdate() {
        networkUpdateTicks--;
        if (networkUpdateTicks <= 0) {
            networkUpdateTicks = AWAutomationStatics.energyMinNetworkUpdateFrequency;
            serverNetworkSynch();
        }
    }

    protected void sendSideRotation(Direction side, int value) {
        int valueBits = (value & 0xff);
        sendDataToClient(side.get3DDataValue(), valueBits);
    }

    /**
     * Offers the full output of the given side to the neighbour and drains only what it took. A neighbour that is not
     * a torque tile gets energy through its FE capability (1 torque = torqueToRf FE).
     *
     * @return the torque that left through the side
     */
    protected final double transferPowerTo(Direction from) {
        ITorqueTile[] tc = getTorqueCache();
        ITorqueTile neighbour = tc[from.get3DDataValue()];
        if (neighbour != null) {
            if (neighbour instanceof BlockEntity neighbourEntity && neighbourEntity.isRemoved()) {
                tc[from.get3DDataValue()] = null; //stale (chunk unloaded); rebuilt on the next neighbour change
                return 0;
            }
            if (neighbour.canInputTorque(from.getOpposite())) {
                return drainTorque(from, neighbour.addTorque(from.getOpposite(), getMaxTorqueOutput(from)));
            }
            return 0;
        }
        return transferEnergyTo(from);
    }

    /** Was RFProxyActual.transferPower. */
    private double transferEnergyTo(Direction from) {
        if (AWAutomationStatics.torqueToRf <= 0) {
            return 0;
        }
        IEnergyStorage target = getNeighbourEnergy(from);
        if (target == null) {
            return 0;
        }
        int received = target.receiveEnergy((int) (getMaxTorqueOutput(from) * AWAutomationStatics.torqueToRf), false);
        return drainTorque(from, received * AWAutomationStatics.rfToTorque);
    }

    protected final double applyPowerDrain(TorqueCell cell) {
        double e = cell.getEnergy();
        cell.setEnergy(e * cell.getEfficiency());
        return cell.getEnergy() - e;
    }

    protected final void sendDataToClient(int type, int data) {
        TorqueNetwork.sendEvent(this, type, data);
    }

    protected final float getRenderRotation(double rotation, double lastRotationDiff, float partialTicks) {
        return (float) (rotation - (lastRotationDiff * (1 - partialTicks)));
    }

    /**
     * Client side: a type/value message sent with {@link #sendDataToClient}. Types 0 to 5 are the animation percentage of
     * that side (the ordinal of the direction), subclasses add their own (7 is the powered flag of the flywheel controller).
     */
    protected void handleClientEvent(int a, int b) {
        if (a < DIRECTION_LENGTH) {
            networkUpdateTicks = AWAutomationStatics.energyMinNetworkUpdateFrequency;
            handleClientRotationData(DIRECTIONS[a], b);
        }
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (TorqueNetwork.isEvent(tag)) {
            if (level != null && level.isClientSide) {
                handleClientEvent(TorqueNetwork.eventType(tag), TorqueNetwork.eventData(tag));
            }
        } else {
            super.onDataPacket(net, packet, registries);
        }
    }

    /** Render bounds for the block entity renderer (it asks for them in {@code getRenderBoundingBox(BlockEntity)}). */
    public AABB getRenderBounds() {
        return new AABB(worldPosition);
    }

    protected static boolean epsilonEquals(double a, double b) {
        return Mth.equal((float) a, (float) b);
    }

    // ************************************** NBT / DATA PACKET ***************************************//
    // The orientation is the facing of the block state now, so it is neither saved nor sent here.

    @Override
    protected void handleUpdateNBT(CompoundTag tag) {
        super.handleUpdateNBT(tag);
        this.invalidateTorqueCache(); //TODO is this needed on client??
    }
}
