/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWarehouseControl.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfare.inventory.ItemQuantityMap;
import io.github.capttracker.ancientwarfare.util.InventoryTools.ComparatorItemHashEntry.SortOrder;
import io.github.capttracker.ancientwarfare.util.InventoryTools.ComparatorItemHashEntry.SortType;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * The warehouse control block's screen: the stored items as a scrollable, searchable grid (virtual slots drawn by the
 * screen, not menu slots) plus the player's inventory. Shift-clicking a player slot stores the stack.
 *
 * <p>Protocol (original keys). Server to client: {@code changeList} (see {@link ItemMapSync}), {@code maxStorage},
 * {@code sortType}, {@code sortOrder} (bytes). Client to server: {@code slotClick} (see {@link WarehouseRequests}),
 * {@code sortType}, {@code sortOrder}, and {@code open} = {@code bounds} to get the work area screen.
 */
public class WarehouseControlMenu extends AutomationMenu<WarehouseBlockEntity> implements WarehouseBlockEntity.Viewer {
    private static final String MAX_STORAGE_TAG = "maxStorage";
    private static final String SORT_TYPE_TAG = "sortType";
    private static final String SORT_ORDER_TAG = "sortOrder";
    private static final String OPEN_TAG = "open";
    private static final String OPEN_BOUNDS = "bounds";

    private final ItemMapSync sync = new ItemMapSync();
    /** The items as the client knows them (client side) or as sent so far (server side). */
    public final ItemQuantityMap itemMap = sync.map();
    private boolean shouldUpdate = true;
    public int maxStorage = 0;
    public int currentStored = 0;

    private SortType sortType;
    private SortOrder sortOrder;
    // server: the values the client has
    @Nullable
    private SortType sentSortType;
    @Nullable
    private SortOrder sentSortOrder;
    private int sentMaxStorage = -1;

    /** Client side. */
    public WarehouseControlMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, WarehouseBlockEntity.class));
    }

    public WarehouseControlMenu(int containerId, Inventory inventory, WarehouseBlockEntity warehouse) {
        super(AwAutomationMenus.WAREHOUSE_CONTROL.get(), containerId, inventory, warehouse);
        sortType = warehouse.getSortType();
        sortOrder = warehouse.getSortOrder();
        addPlayerSlots(inventory, 8, 142, 4);
        warehouse.addViewer(this);
    }

    @Override
    public void removed(Player who) {
        blockEntity.removeViewer(this);
        super.removed(who);
    }

    /** Server, from the block entity: the content changed. */
    @Override
    public void onWarehouseInventoryUpdated() {
        shouldUpdate = true;
    }

    /** Original: puts the stack into the warehouse (server) and reports empty either way. */
    @Override
    public ItemStack quickMoveStack(Player who, int slotClickedIndex) {
        if (isClient()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(slotClickedIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        stack = blockEntity.tryAdd(stack);
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return ItemStack.EMPTY;
    }

    // ---- server to client ----

    @Override
    protected void sendInitialState() {
        sendMaxStorageAndSort();
    }

    private void sendMaxStorageAndSort() {
        sentMaxStorage = blockEntity.getMaxStorage();
        sentSortType = blockEntity.getSortType();
        sentSortOrder = blockEntity.getSortOrder();
        CompoundTag tag = new CompoundTag();
        tag.putInt(MAX_STORAGE_TAG, sentMaxStorage);
        tag.putByte(SORT_ORDER_TAG, (byte) sentSortOrder.ordinal());
        tag.putByte(SORT_TYPE_TAG, (byte) sentSortType.ordinal());
        sendDataToClient(tag);
    }

    @Override
    protected void detectChanges() {
        if (shouldUpdate) {
            sync.sendChanges(registries(), blockEntity::getItems, this::sendDataToClient);
            shouldUpdate = false;
        }
        if (sentMaxStorage != blockEntity.getMaxStorage() || sentSortType != blockEntity.getSortType()
                || sentSortOrder != blockEntity.getSortOrder()) {
            sendMaxStorageAndSort();
        }
    }

    // ---- both directions ----

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (isClient()) {
            handleServerData(tag);
        } else if (isRequestAllowed()) {
            handleClientData(tag);
        }
    }

    private void handleServerData(CompoundTag tag) {
        sync.handle(registries(), tag);
        if (tag.contains(MAX_STORAGE_TAG, Tag.TAG_INT)) {
            maxStorage = tag.getInt(MAX_STORAGE_TAG);
        }
        if (tag.contains(SORT_TYPE_TAG, Tag.TAG_BYTE)) {
            SortType[] types = SortType.values();
            int index = tag.getByte(SORT_TYPE_TAG);
            if (index >= 0 && index < types.length) {
                sortType = types[index];
            }
        }
        if (tag.contains(SORT_ORDER_TAG, Tag.TAG_BYTE)) {
            SortOrder[] orders = SortOrder.values();
            int index = tag.getByte(SORT_ORDER_TAG);
            if (index >= 0 && index < orders.length) {
                sortOrder = orders[index];
            }
        }
        currentStored = itemMap.getTotalItemCount();
        refreshGui();
    }

    private void handleClientData(CompoundTag tag) {
        if (tag.contains(WarehouseRequests.SLOT_CLICK_TAG, Tag.TAG_COMPOUND)) {
            WarehouseRequests.SlotClick click = WarehouseRequests.readSlotClick(registries(), tag);
            if (click != null && player instanceof ServerPlayer serverPlayer) {
                blockEntity.handleSlotClick(serverPlayer, click.item(), click.shift(), click.right());
            }
            return;
        }
        if (tag.contains(SORT_TYPE_TAG, Tag.TAG_BYTE)) {
            SortType[] types = SortType.values();
            int index = tag.getByte(SORT_TYPE_TAG);
            if (index >= 0 && index < types.length) {
                blockEntity.setSortType(types[index]);
                sentSortType = types[index]; // the client already shows it
            }
        }
        if (tag.contains(SORT_ORDER_TAG, Tag.TAG_BYTE)) {
            SortOrder[] orders = SortOrder.values();
            int index = tag.getByte(SORT_ORDER_TAG);
            if (index >= 0 && index < orders.length) {
                blockEntity.setSortOrder(orders[index]);
                sentSortOrder = orders[index];
            }
        }
        if (tag.contains(OPEN_TAG, Tag.TAG_STRING) && OPEN_BOUNDS.equals(tag.getString(OPEN_TAG))
                && player instanceof ServerPlayer serverPlayer) {
            AwAutomationMenus.openBounds(serverPlayer, blockEntity);
        }
    }

    // ---- client input (the screen calls these) ----

    /**
     * Client: a click on an item (or, with {@code stack} empty, on empty space with an item on the cursor).
     *
     * @param count how many the clicked slot shows; at most one stack of it is requested
     */
    public void handleClientRequestSpecific(ItemStack stack, int count, boolean isShiftClick, boolean isRightClick) {
        sendDataToServer(WarehouseRequests.writeSlotClick(registries(), stack, count, isShiftClick, isRightClick));
    }

    public SortType getSortType() {
        return sortType;
    }

    public void setSortType(SortType type) {
        sortType = type;
        CompoundTag tag = new CompoundTag();
        tag.putByte(SORT_TYPE_TAG, (byte) type.ordinal());
        sendDataToServer(tag);
    }

    public SortOrder getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(SortOrder order) {
        sortOrder = order;
        CompoundTag tag = new CompoundTag();
        tag.putByte(SORT_ORDER_TAG, (byte) order.ordinal());
        sendDataToServer(tag);
    }

    public boolean isWarehouseFull() {
        return currentStored == maxStorage;
    }

    /** Client: asks the server for the work area screen (original: {@code openGui(GUI_WORKSITE_BOUNDS)}). */
    public void requestOpenBounds() {
        sendDataToServer(OPEN_TAG, StringTag.valueOf(OPEN_BOUNDS));
    }
}
