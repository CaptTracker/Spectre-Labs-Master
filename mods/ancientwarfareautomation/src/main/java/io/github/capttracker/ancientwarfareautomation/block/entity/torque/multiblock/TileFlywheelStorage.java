/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/torque/multiblock/TileFlywheelStorage.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.torque.multiblock;

import io.github.capttracker.ancientwarfare.block.entity.UpdatableBlockEntity;
import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TorqueNetwork;
import io.github.capttracker.ancientwarfareautomation.block.torque.IBlockEntityTypeProvider;
import io.github.capttracker.ancientwarfareautomation.block.torque.ITieredBlock;
import io.github.capttracker.ancientwarfareautomation.block.torque.TorqueTier;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * One block of a flywheel storage stack. Blocks of the same tier that touch form a stack that is valid when it is a
 * full 1x1xh or 3x3xh column; the control block (the base block of a 1-wide stack, the middle of the base of a 3-wide
 * one) holds the stored energy: {@code width * width * height} times the tier's energy per block. The flywheel controller
 * on top of the stack reads and writes the public fields of the control block.
 *
 * <p>The tier is the block now (it was the block metadata); the validators match "the same block". Validation runs
 * when a block is placed ({@link #blockPlaced()}, called by the block after placing) and when one is broken
 * ({@link #blockBroken()}, called by the block after it is gone).
 */
public class TileFlywheelStorage extends UpdatableBlockEntity {

    private static final String CONTROLLER_POS_TAG = "controllerPos";
    private static final String IS_CONTROL_TAG = "isControl";
    private static final String SET_WIDTH_TAG = "setWidth";
    private static final String SET_HEIGHT_TAG = "setHeight";
    /** Event type of the stored energy percentage (the original used type 1). */
    private static final int ENERGY_EVENT = 1;
    @Nullable
    public BlockPos controllerPos;
    public boolean isControl = false;//set to true if this is the control block for a setup
    public int setWidth;
    public int setHeight;
    private int setCube;//validation params, only 'valid' in the control block.  used by rendering
    public double storedEnergy;
    public double maxEnergyStored;
    private final double maxRpm = 100;
    public double torqueLoss;
    public double rotation;//used in rendering
    public double lastRotationDiff;
    private int clientEnergy;
    private int clientDestEnergy;
    private int networkUpdateTicks = 0;

    public TileFlywheelStorage(BlockPos pos, BlockState state) {
        super(IBlockEntityTypeProvider.typeOf(state), pos, state);
    }

    /** Called every tick on both sides by the block's ticker. */
    public void update() {
        if (isControl) {
            if (level.isClientSide) {
                clientNetworkUpdate();
            } else {
                serverNetworkUpdate();
                applyPowerLoss();
            }
        }
    }

    public TorqueTier getTier() {
        return getBlockState().getBlock() instanceof ITieredBlock tiered ? tiered.getTier() : TorqueTier.LIGHT;
    }

    protected void clientNetworkUpdate() {
        updateRotation();
        if (networkUpdateTicks > 0) {
            int diff = clientDestEnergy - clientEnergy;
            clientEnergy += diff / networkUpdateTicks;
            networkUpdateTicks--;
        }
    }

    protected void applyPowerLoss() {
        double eff = 1.d - getEfficiency();
        eff *= 0.1d;
        torqueLoss = storedEnergy * eff;
        storedEnergy -= torqueLoss;
    }

    protected double getEfficiency() {
        return getTier().getEfficiency();
    }

    private void serverNetworkUpdate() {
        if (!AWAutomationStatics.enable_energy_network_updates) {
            return;
        }
        networkUpdateTicks--;
        if (networkUpdateTicks <= 0) {
            double percentStored = storedEnergy / maxEnergyStored;
            int total = (int) (percentStored * 100.d);
            if (total != clientEnergy) {
                clientEnergy = total;
                TorqueNetwork.sendEvent(this, ENERGY_EVENT, clientEnergy);
            }
            networkUpdateTicks = AWAutomationStatics.energyMinNetworkUpdateFrequency;
        }
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (TorqueNetwork.isEvent(tag)) {
            if (level != null && level.isClientSide && TorqueNetwork.eventType(tag) == ENERGY_EVENT) {
                clientDestEnergy = TorqueNetwork.eventData(tag);
                networkUpdateTicks = AWAutomationStatics.energyMinNetworkUpdateFrequency;
            }
        } else {
            super.onDataPacket(net, packet, registries);
        }
    }

    private void updateRotation() {
        double rpm = (double) clientEnergy * 0.01d * this.maxRpm;
        lastRotationDiff = -(rpm * AWAutomationStatics.rpmToRpt) * Trig.TORADIANS;
        rotation += lastRotationDiff;
        rotation %= Trig.PI * 2;
    }

    /** Rotation of the stack in radians for the given partial tick, for the renderer. */
    public float getRotation(float delta) {
        return (float) (rotation - (lastRotationDiff * (1 - delta)));
    }

    /** Whole number percent (0 to 100) of the stack's capacity that the client animation shows. */
    public int getClientEnergyPercent() {
        return clientEnergy;
    }

    public int getSetWidth() {
        return setWidth;
    }

    public int getSetHeight() {
        return setHeight;
    }

    /** Called by the block after it was placed (server). */
    public final void blockPlaced() {
        validateSetup();
    }

    /** Called by the block after it was removed from the world (server), so the neighbours see it as gone. */
    public void blockBroken() {
        informNeighborsToValidate();
    }

    private void setController(@Nullable BlockPos controllerPos) {
        this.controllerPos = controllerPos;
        isControl = (controllerPos == null || controllerPos.equals(worldPosition));
        if (controllerPos == null) {
            setWidth = 1;
            setHeight = 1;
            setCube = 1;
        }

        setChanged();
        syncToClients();
    }

    private boolean validateSetup() {
        if (level == null || level.isClientSide) {
            return false;
        }
        Block block = getBlockState().getBlock();
        Predicate<BlockState> sameBlock = s -> s.is(block);
        MultiblockScan.Region region = MultiblockScan.cross(level, worldPosition, sameBlock);
        int minX = region.min().getX();
        int minY = region.min().getY();
        int minZ = region.min().getZ();
        int w = region.sizeX();
        int h = region.sizeY();
        int l = region.sizeZ();
        boolean valid = w == l && (w == 1 || w == 3) && MultiblockScan.isBoxFilled(level, region, sameBlock);
        if (valid) {
            int cx = w == 1 ? minX : minX + 1;
            int cz = l == 1 ? minZ : minZ + 1;
            setValidSetup(MultiblockScan.boxPositions(region), cx, minY, cz, w, h, getTier());
        } else {
            setInvalidSetup(region.positions());
        }
        return valid;
    }

    private void setValidSetup(Set<BlockPos> set, int cx, int cy, int cz, int size, int height, TorqueTier tier) {
        controllerPos = new BlockPos(cx, cy, cz);
        final BlockPos control = controllerPos;
        Optional<TileFlywheelStorage> te = WorldTools.getTile(level, control, TileFlywheelStorage.class);
        if (te.isPresent()) {
            te.get().setAsController(size, height, tier);
            for (BlockPos pos : set) {
                WorldTools.getTile(level, pos, TileFlywheelStorage.class).ifPresent(t -> t.setController(control));
            }
        } else {
            controllerPos = null;
        }
    }

    private void setAsController(int size, int height, TorqueTier tier) {
        this.isControl = true;
        this.setWidth = size;
        this.setHeight = height;
        this.setCube = size * size * height;
        this.maxEnergyStored = (double) setCube * tier.getStorageEnergyMax();
        setChanged();
        syncToClients();
    }

    private void setInvalidSetup(Set<BlockPos> set) {
        setController(null);
        for (BlockPos pos : set) {
            WorldTools.getTile(level, pos, TileFlywheelStorage.class).ifPresent(t -> t.setController(null));
        }
    }

    private void informNeighborsToValidate() {
        for (Direction d : Direction.values()) {
            WorldTools.getTile(level, worldPosition.relative(d), TileFlywheelStorage.class).ifPresent(TileFlywheelStorage::validateSetup);
        }
    }

    @Override
    protected void writeUpdateNBT(CompoundTag tag) {
        super.writeUpdateNBT(tag);
        if (controllerPos != null) {
            tag.putLong(CONTROLLER_POS_TAG, controllerPos.asLong());
        }
        if (isControl) {
            tag.putBoolean(IS_CONTROL_TAG, true);
            tag.putInt(SET_WIDTH_TAG, setWidth);
            tag.putInt(SET_HEIGHT_TAG, setHeight);
            tag.putInt("clientEnergy", clientEnergy);
        }
    }

    @Override
    protected void handleUpdateNBT(CompoundTag tag) {
        super.handleUpdateNBT(tag);
        controllerPos = tag.contains(CONTROLLER_POS_TAG) ? BlockPos.of(tag.getLong(CONTROLLER_POS_TAG)) : null;
        isControl = tag.getBoolean(IS_CONTROL_TAG);
        if (isControl) {
            setHeight = tag.getInt(SET_HEIGHT_TAG);
            setWidth = tag.getInt(SET_WIDTH_TAG);
            setCube = setWidth * setWidth * setHeight;
            clientEnergy = tag.getInt("clientEnergy");
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPos = tag.contains(CONTROLLER_POS_TAG) ? BlockPos.of(tag.getLong(CONTROLLER_POS_TAG)) : null;
        isControl = tag.getBoolean(IS_CONTROL_TAG);
        if (isControl) {
            maxEnergyStored = tag.getDouble("maxEnergy");
            setHeight = tag.getInt(SET_HEIGHT_TAG);
            setWidth = tag.getInt(SET_WIDTH_TAG);
            setCube = setWidth * setWidth * setHeight;
        }
        storedEnergy = tag.getDouble("storedEnergy");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) {
            tag.putLong(CONTROLLER_POS_TAG, controllerPos.asLong());
        }
        if (isControl) {
            tag.putBoolean(IS_CONTROL_TAG, true);
            tag.putDouble("maxEnergy", maxEnergyStored);
            tag.putInt(SET_WIDTH_TAG, setWidth);
            tag.putInt(SET_HEIGHT_TAG, setHeight);
        }
        tag.putDouble("storedEnergy", storedEnergy);
    }

    /** Render bounds for the block entity renderer: the control block draws the whole stack. */
    public AABB getRenderBounds() {
        return new AABB(worldPosition.getX() - 1, worldPosition.getY() - 1, worldPosition.getZ() - 1, worldPosition.getX() + 2, worldPosition.getY() + setHeight, worldPosition.getZ() + 2);
    }

}
