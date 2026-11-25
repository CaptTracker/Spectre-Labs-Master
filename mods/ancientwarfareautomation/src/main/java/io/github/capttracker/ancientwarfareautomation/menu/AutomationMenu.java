/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/container/ContainerTileBase and the sendInitData / detectAndSendChanges pattern of
 * the automation/container classes.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfare.menu.BlockEntityMenu;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

/**
 * Base of the Automation menus. It adds two things to {@link BlockEntityMenu}:
 * <ul>
 * <li>The first-state push. Several block entities open their menu without calling {@code sendInitData()} afterwards
 * (the worksites, the stirling generator, the chunk loader), so the server copy sends its starting state itself on its
 * first {@link #broadcastChanges()}. If the opener does call {@code sendInitData()} (the warehouse hooks do) the state
 * goes out at once and is not sent a second time.</li>
 * <li>{@link #detectChanges()}, the server side check that replaces the original's {@code detectAndSendChanges}
 * override: it runs every tick the menu is open, after the first push.</li>
 * </ul>
 * Everything a client sends reaches {@code handlePacketData} on the server; subclasses must treat it as untrusted and
 * check {@link #isRequestAllowed()} first.
 */
public abstract class AutomationMenu<T extends BlockEntity> extends BlockEntityMenu<T> {
    private boolean initSent = false;

    protected AutomationMenu(@Nullable MenuType<?> type, int containerId, Inventory playerInventory, T blockEntity) {
        super(type, containerId, playerInventory, blockEntity);
    }

    protected final boolean isClient() {
        return player.level().isClientSide;
    }

    protected final HolderLookup.Provider registries() {
        return player.level().registryAccess();
    }

    /**
     * Whether a request that arrived from a client may be acted on: this is the server copy of the menu and the block
     * entity is still there and in reach of the player (a client could send data long after it walked away).
     */
    protected final boolean isRequestAllowed() {
        Player who = player;
        return !who.level().isClientSide && stillValid(who);
    }

    @Override
    public final void sendInitData() {
        initSent = true;
        sendInitialState();
    }

    /** Server: sends everything the screen needs to open. Called once, by the opener or on the first tick. */
    protected void sendInitialState() {
    }

    /** Server: called every tick the menu is open (after the first push). Send changes to the client from here. */
    protected void detectChanges() {
    }

    @Override
    public void broadcastChanges() {
        if (!isClient()) {
            if (!initSent) {
                sendInitData();
            }
            detectChanges();
        }
        super.broadcastChanges();
    }
}
