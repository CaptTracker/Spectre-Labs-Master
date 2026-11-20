/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/container/ContainerTileBase.
 */
package io.github.capttracker.ancientwarfare.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

/**
 * A menu for one block entity. Server side it is built from the block entity; client side from the position the
 * server wrote when it opened the menu (see {@link #resolve}). The menu closes when the block entity goes away or
 * the player is more than 8 blocks from it (same limit as the original: squared distance 64).
 */
public abstract class BlockEntityMenu<T extends BlockEntity> extends AwMenu {
    protected final T blockEntity;

    protected BlockEntityMenu(@Nullable MenuType<?> type, int containerId, Inventory playerInventory, T blockEntity) {
        super(type, containerId, playerInventory);
        this.blockEntity = blockEntity;
    }

    public T blockEntity() {
        return blockEntity;
    }

    /**
     * Client side: reads the block position the server wrote and finds the block entity of that class there.
     *
     * @throws IllegalStateException if the block entity is not loaded on the client (the original threw as well)
     */
    public static <T extends BlockEntity> T resolve(Inventory inventory, RegistryFriendlyByteBuf buf, Class<T> type) {
        BlockPos pos = buf.readBlockPos();
        BlockEntity found = inventory.player.level().getBlockEntity(pos);
        if (type.isInstance(found)) {
            return type.cast(found);
        }
        throw new IllegalStateException("No " + type.getSimpleName() + " at " + pos + " for the menu the server opened");
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity.isRemoved() || blockEntity.getLevel() == null || blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos()) != blockEntity) {
            return false;
        }
        BlockPos pos = blockEntity.getBlockPos();
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64D;
    }
}
