/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the GUI opening of the warehouse tiles and the mailbox block
 * (NetworkHandler.openGui with GUI_WAREHOUSE_CONTROL, _STORAGE, _OUTPUT, _CRAFTING, _STOCK, _STOCK_LINKER and
 * GUI_MAILBOX_INVENTORY).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.warehouse;

import io.github.capttracker.ancientwarfare.menu.AwMenu;
import io.github.capttracker.ancientwarfareautomation.gamedata.MailboxBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

/**
 * Where the warehouse blocks and the mailbox get the menus they open. The menus and screens belong to the GUI slice
 * of this module, which hands its menu constructors to the {@code set...Factory} methods during setup (the same
 * pattern as {@code TileStirlingGenerator.setMenuFactory}). Until a factory is set, right-clicking the block does
 * nothing.
 *
 * <p>The menu is opened with the block position as extra data ({@code BlockEntityMenu.resolve} reads it on the
 * client) and {@code AwMenu.sendInitData()} is called right after, as {@code AwMenu} asks. The title is the name of
 * the block. For the crafting station the menu finds the warehouse through
 * {@link WarehouseCraftingStationBlockEntity#getWarehouse()}.
 */
public final class WarehouseMenus {
    private WarehouseMenus() {}

    /** Creates the menu of a block entity: {@code (containerId, playerInventory, blockEntity)}, server side. */
    @FunctionalInterface
    public interface Factory<T extends BlockEntity> {
        AbstractContainerMenu create(int containerId, Inventory playerInventory, T blockEntity);
    }

    @Nullable
    private static volatile Factory<WarehouseBlockEntity> controlFactory;
    @Nullable
    private static volatile Factory<WarehouseStorageBlockEntity> storageFactory;
    @Nullable
    private static volatile Factory<WarehouseInterfaceBlockEntity> interfaceFactory;
    @Nullable
    private static volatile Factory<WarehouseCraftingStationBlockEntity> craftingStationFactory;
    @Nullable
    private static volatile Factory<WarehouseStockViewerBlockEntity> stockViewerFactory;
    @Nullable
    private static volatile Factory<WarehouseStockLinkerBlockEntity> stockLinkerFactory;
    @Nullable
    private static volatile Factory<MailboxBlockEntity> mailboxFactory;

    /** TODO(port): called by the GUI slice (1.12.2 {@code GUI_WAREHOUSE_CONTROL}, {@code ContainerWarehouseControl}). */
    public static void setControlFactory(@Nullable Factory<WarehouseBlockEntity> factory) {
        controlFactory = factory;
    }

    /** TODO(port): called by the GUI slice ({@code GUI_WAREHOUSE_STORAGE}, {@code ContainerWarehouseStorage}). */
    public static void setStorageFactory(@Nullable Factory<WarehouseStorageBlockEntity> factory) {
        storageFactory = factory;
    }

    /** TODO(port): called by the GUI slice ({@code GUI_WAREHOUSE_OUTPUT}, {@code ContainerWarehouseInterface}). */
    public static void setInterfaceFactory(@Nullable Factory<WarehouseInterfaceBlockEntity> factory) {
        interfaceFactory = factory;
    }

    /** TODO(port): called by the GUI slice ({@code GUI_WAREHOUSE_CRAFTING}, {@code ContainerWarehouseCraftingStation}). */
    public static void setCraftingStationFactory(@Nullable Factory<WarehouseCraftingStationBlockEntity> factory) {
        craftingStationFactory = factory;
    }

    /** TODO(port): called by the GUI slice ({@code GUI_WAREHOUSE_STOCK}, {@code ContainerWarehouseStockViewer}). */
    public static void setStockViewerFactory(@Nullable Factory<WarehouseStockViewerBlockEntity> factory) {
        stockViewerFactory = factory;
    }

    /** TODO(port): called by the GUI slice ({@code GUI_WAREHOUSE_STOCK_LINKER}, {@code ContainerWarehouseStockLinker}). */
    public static void setStockLinkerFactory(@Nullable Factory<WarehouseStockLinkerBlockEntity> factory) {
        stockLinkerFactory = factory;
    }

    /** TODO(port): called by the GUI slice ({@code GUI_MAILBOX_INVENTORY}, {@code ContainerMailbox}). */
    public static void setMailboxFactory(@Nullable Factory<MailboxBlockEntity> factory) {
        mailboxFactory = factory;
    }

    public static void openControl(Player player, WarehouseBlockEntity blockEntity) {
        open(player, blockEntity, controlFactory);
    }

    public static void openStorage(Player player, WarehouseStorageBlockEntity blockEntity) {
        open(player, blockEntity, storageFactory);
    }

    public static void openInterface(Player player, WarehouseInterfaceBlockEntity blockEntity) {
        open(player, blockEntity, interfaceFactory);
    }

    public static void openCraftingStation(Player player, WarehouseCraftingStationBlockEntity blockEntity) {
        open(player, blockEntity, craftingStationFactory);
    }

    public static void openStockViewer(Player player, WarehouseStockViewerBlockEntity blockEntity) {
        open(player, blockEntity, stockViewerFactory);
    }

    public static void openStockLinker(Player player, WarehouseStockLinkerBlockEntity blockEntity) {
        open(player, blockEntity, stockLinkerFactory);
    }

    public static void openMailbox(Player player, MailboxBlockEntity blockEntity) {
        open(player, blockEntity, mailboxFactory);
    }

    /** Server only (does nothing for a client player or while no factory is set). */
    private static <T extends BlockEntity> void open(Player player, T blockEntity, @Nullable Factory<T> factory) {
        if (factory == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        serverPlayer.openMenu(
                new SimpleMenuProvider((id, inventory, p) -> factory.create(id, inventory, blockEntity), blockEntity.getBlockState().getBlock().getName()),
                buf -> buf.writeBlockPos(blockEntity.getBlockPos()));
        if (serverPlayer.containerMenu instanceof AwMenu menu) {
            menu.sendInitData();
        }
    }
}
