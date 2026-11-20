/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/warehouse2/TileControlled.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.warehouse;

import io.github.capttracker.ancientwarfare.block.entity.UpdatableBlockEntity;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Base of the warehouse blocks that attach to a control block (storage, interface, stock viewer, stock linker).
 *
 * <p><b>Finding the controller</b> (server only, from the first tick after the block entity was created or loaded):
 * the controller position saved last time is tried first ({@code controllerPosition}); if that does not work the
 * blocks in a box of 33 x 9 x 33 around this one are searched for the first valid {@link IControllerTile}
 * ({@link #searchForController()}). The control block also scans its own bounds when it starts and attaches every
 * orphan it finds, so either side can come first.
 *
 * <p><b>Lifecycle</b> (1.12.2 {@code invalidate}/{@code validate}): {@link #setRemoved()} detaches from the controller,
 * which runs when the block is broken and when its chunk unloads. {@code validate} is not ported: it could only
 * re-attach a controller that {@code invalidate} had just cleared. {@link #getController()} drops a controller that was
 * removed meanwhile (a chunk reload creates a new control block entity; the old one must not stay attached).
 *
 * <p>Ticking is server only (the block's {@code getTicker} returns {@link #ticker(Level)}); the client copy only
 * receives what the subclasses put into the update tag. The update tag hooks have the registry access that item
 * stacks need ({@link #writeSyncData}, {@link #readSyncData}); Core's {@code UpdatableBlockEntity} only has the plain
 * tag hooks.
 */
public abstract class ControlledBlockEntity extends UpdatableBlockEntity implements IControlledTile {
    private static final String CONTROLLER_POSITION_TAG = "controllerPosition";

    private boolean init;
    @Nullable
    private WarehouseBlockEntity controller;
    @Nullable
    private BlockPos controllerPosition;

    protected ControlledBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The ticker for the block's {@code getTicker}: runs {@link #update()} on the server and nothing on the client. */
    @Nullable
    public static <T extends BlockEntity> BlockEntityTicker<T> ticker(Level level) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, state, blockEntity) -> {
            if (blockEntity instanceof ControlledBlockEntity controlled) {
                controlled.update();
            }
        };
    }

    public final void update() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!init) {
            init = true;
            if (!loadController()) {
                searchForController();
            }
        }
        updateTile();
    }

    private boolean loadController() {
        BlockPos pos = controllerPosition;
        controllerPosition = null;
        if (pos != null && controller == null && level != null && level.isLoaded(pos)) {
            if (level.getBlockEntity(pos) instanceof IControllerTile tile && isValidController(tile)) {
                tile.addControlledTile(this);
            }
        }
        return controller != null;
    }

    /** One server tick, after the controller was looked for. */
    protected abstract void updateTile();

    /** Looks for a control block in a 33 x 9 x 33 box around this block and attaches to the first valid one. */
    public void searchForController() {
        if (level == null) {
            return;
        }
        BlockPos pos = worldPosition;
        for (BlockEntity be : WarehouseScan.loadedTilesInArea(level, pos.getX() - 16, pos.getY() - 4, pos.getZ() - 16,
                pos.getX() + 16, pos.getY() + 4, pos.getZ() + 16)) {
            if (be instanceof IControllerTile tile && isValidController(tile)) {
                tile.addControlledTile(this);
                break;
            }
        }
    }

    /** The default rule: this block lies inside the bounds of the controller. */
    @Override
    public boolean isValidController(IControllerTile tile) {
        return BlockTools.isPositionWithinBounds(worldPosition, tile.getWorkBoundsMin(), tile.getWorkBoundsMax());
    }

    @Override
    public void setRemoved() {
        if (controller != null) {
            controller.removeControlledTile(this);
        }
        controller = null;
        init = false;
        super.setRemoved();
    }

    @Override
    public final void setController(@Nullable WarehouseBlockEntity tile) {
        this.controller = tile;
        this.controllerPosition = tile == null ? null : tile.getControllerPosition();
        onControllerChanged();
    }

    protected void onControllerChanged() {
    }

    @Override
    public final Optional<WarehouseBlockEntity> getController() {
        if (controller != null && controller.isRemoved()) {
            controller = null;
        }
        return Optional.ofNullable(controller);
    }

    @Override
    public BlockPos getPosition() {
        return worldPosition;
    }

    // ************************************** NBT AND PACKET DATA METHODS ***************************************//

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPosition = tag.contains(CONTROLLER_POSITION_TAG) ? BlockPos.of(tag.getLong(CONTROLLER_POSITION_TAG)) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPosition != null) {
            tag.putLong(CONTROLLER_POSITION_TAG, controllerPosition.asLong());
        }
    }

    /** Fills the update tag (client sync). Like {@code writeUpdateNBT}, with the registry access item stacks need. */
    protected void writeSyncData(CompoundTag tag, HolderLookup.Provider registries) {
    }

    /** Reads what {@link #writeSyncData} wrote (client). */
    protected void readSyncData(CompoundTag tag, HolderLookup.Provider registries) {
    }

    @Override
    public final CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeSyncData(tag, registries);
        return tag;
    }

    @Override
    public final void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        readSyncData(tag, registries);
    }

    @Override
    public final void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        super.onDataPacket(net, packet, registries);
        readSyncData(packet.getTag(), registries);
    }

    // ************************************** IDENTITY ***************************************//

    @Override
    public final boolean equals(Object obj) {
        return this == obj
                || (obj instanceof ControlledBlockEntity other && this.level == other.level && this.worldPosition.equals(other.worldPosition));
    }

    @Override
    public final int hashCode() {
        return worldPosition.hashCode();
    }
}
