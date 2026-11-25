/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/container/ContainerBase.
 */
package io.github.capttracker.ancientwarfare.menu;

import io.github.capttracker.ancientwarfare.network.MenuDataPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Base for every Ancient Warfare menu. Besides slots it gives each menu a free-form NBT channel
 * between its client and server copies, which the original mod's screens rely on heavily.
 */
public abstract class AwMenu extends AbstractContainerMenu {
    public final Player player;
    /** Number of player-inventory slots added by {@link #addPlayerSlots}. */
    protected int playerSlots = 0;
    @Nullable
    private Consumer<CompoundTag> screenListener;
    @Nullable
    private Runnable refreshListener;

    protected AwMenu(@Nullable MenuType<?> type, int containerId, Inventory playerInventory) {
        super(type, containerId);
        this.player = playerInventory.player;
    }

    /**
     * Adds the 27 main slots at (x, y) and the hotbar below them.
     *
     * @param gap pixels between the main block and the hotbar (vanilla screens use 4)
     * @return the y coordinate just below the hotbar
     */
    protected int addPlayerSlots(Inventory inventory, int x, int y, int gap) {
        for (int col = 0; col < 9; col++) {
            if (canAddHotbarSlot(col)) {
                addSlot(new Slot(inventory, col, x + col * 18, y + gap + 3 * 18));
                playerSlots++;
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, row * 9 + col + 9, x + col * 18, y + row * 18));
                playerSlots++;
            }
        }
        return y + 4 * 18 + gap;
    }

    /** Lets a menu leave out a hotbar slot, for example the one holding the item that opened it. */
    protected boolean canAddHotbarSlot(int hotbarIndex) {
        return true;
    }

    // ---- NBT channel ----

    /** Client only: sends data to the server copy of this menu. */
    protected void sendDataToServer(CompoundTag data) {
        if (player.level().isClientSide) {
            PacketDistributor.sendToServer(new MenuDataPayload(data, false));
        }
    }

    protected void sendDataToServer(String key, Tag value) {
        CompoundTag data = new CompoundTag();
        data.put(key, value);
        sendDataToServer(data);
    }

    /** Server only: sends data to the client copy of this menu ({@link #handlePacketData}). */
    protected void sendDataToClient(CompoundTag data) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new MenuDataPayload(data, false));
        }
    }

    /** Server only: sends data straight to the screen showing this menu. */
    protected void sendDataToScreen(CompoundTag data) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new MenuDataPayload(data, true));
        }
    }

    /** Client only: the screen registers here to receive {@link #sendDataToScreen} data. */
    public final void setScreenListener(@Nullable Consumer<CompoundTag> listener) {
        this.screenListener = listener;
    }

    /** Client only: the screen registers here to be told to rebuild itself (1.12.2: ContainerBase.gui.refreshGui()). */
    public final void setRefreshListener(@Nullable Runnable listener) {
        this.refreshListener = listener;
    }

    /** Client only: asks the open screen to re-run its setup on its next tick. Safe to call with no screen. */
    protected final void refreshGui() {
        if (refreshListener != null && player.level().isClientSide) {
            refreshListener.run();
        }
    }

    public final void onPacketData(CompoundTag data, boolean forScreen) {
        if (forScreen) {
            // A client must never be able to trigger the screen path on the server.
            if (player.level().isClientSide && screenListener != null) {
                screenListener.accept(data);
            }
        } else {
            handlePacketData(data);
        }
    }

    /**
     * Override to react to data from the other side. On the server this data comes from a client
     * and must be validated like any other player input.
     */
    protected void handlePacketData(CompoundTag data) {
    }

    /**
     * Override to push the menu's starting state to the client. Call it from the code that opens
     * the menu, after {@code player.openMenu(...)} returns.
     */
    public void sendInitData() {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** No shift-click support unless a subclass adds it (same default as the original). */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
