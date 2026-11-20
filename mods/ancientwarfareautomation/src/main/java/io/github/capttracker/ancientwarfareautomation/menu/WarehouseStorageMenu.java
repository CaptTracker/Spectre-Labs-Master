/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWarehouseStorage.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfare.inventory.ItemQuantityMap;
import io.github.capttracker.ancientwarfare.util.NBTHelper;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStorageBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStorageFilter;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One storage block's screen: its filter list (at most {@value #MAX_FILTERS}), the items it holds as virtual slots and the
 * player's inventory. Shift-clicking a player slot stores the stack in this block.
 *
 * <p>Protocol (original keys): {@code filterList} both ways (the server sends it on open and when the block's filters
 * change, the client when the player edits them), {@code changeList} server to client ({@link ItemMapSync}) and
 * {@code slotClick} client to server ({@link WarehouseRequests}).
 */
public class WarehouseStorageMenu extends AutomationMenu<WarehouseStorageBlockEntity> implements WarehouseStorageBlockEntity.Viewer {
    public static final int MAX_FILTERS = 10;

    private final ItemMapSync sync = new ItemMapSync();
    private boolean shouldSynch = true;
    /** The items of this storage block as the client knows them (client) or as sent so far (server). */
    public final ItemQuantityMap itemMap = sync.map();
    public List<WarehouseStorageFilter> filters = new ArrayList<>();

    /** Client side. */
    public WarehouseStorageMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, WarehouseStorageBlockEntity.class));
    }

    public WarehouseStorageMenu(int containerId, Inventory inventory, WarehouseStorageBlockEntity storage) {
        super(AwAutomationMenus.WAREHOUSE_STORAGE.get(), containerId, inventory, storage);
        storage.addViewer(this);
        filters.addAll(storage.getFilters());
        addPlayerSlots(inventory, 8, 148 + 8, 4);
    }

    @Override
    public void removed(Player who) {
        blockEntity.removeViewer(this);
        super.removed(who);
    }

    /** Original: puts the stack into this block (server) and reports empty either way. */
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
    public void onStorageInventoryUpdated() {
        shouldSynch = true;
    }

    @Override
    public void onFilterListUpdated() {
        this.filters.clear();
        this.filters.addAll(blockEntity.getFilters());
        sendInitData();
    }

    @Override
    protected void sendInitialState() {
        CompoundTag tag = new CompoundTag();
        NBTHelper.writeSerializablesTo(registries(), tag, WarehouseRequests.FILTER_LIST_TAG, filters);
        sendDataToClient(tag);
    }

    @Override
    protected void detectChanges() {
        if (shouldSynch) {
            sync.sendChanges(registries(), blockEntity::addItems, this::sendDataToClient);
            shouldSynch = false;
        }
    }

    // ---- both directions ----

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (isClient()) {
            if (tag.contains(WarehouseRequests.FILTER_LIST_TAG, Tag.TAG_LIST)) {
                this.filters = NBTHelper.deserializeListFrom(registries(), tag, WarehouseRequests.FILTER_LIST_TAG, WarehouseStorageFilter::new);
                refreshGui();
            } else if (sync.handle(registries(), tag)) {
                refreshGui();
            }
            return;
        }
        if (!isRequestAllowed()) {
            return;
        }
        if (tag.contains(WarehouseRequests.FILTER_LIST_TAG, Tag.TAG_LIST)) {
            List<WarehouseStorageFilter> received = WarehouseRequests.readFilters(registries(), tag, WarehouseStorageFilter::new, MAX_FILTERS);
            List<WarehouseStorageFilter> clean = new ArrayList<>(received.size());
            for (WarehouseStorageFilter filter : received) {
                clean.add(new WarehouseStorageFilter(WarehouseRequests.template(filter.getFilterItem())));
            }
            blockEntity.setFilters(clean);
        } else if (tag.contains(WarehouseRequests.SLOT_CLICK_TAG, Tag.TAG_COMPOUND)) {
            WarehouseRequests.SlotClick click = WarehouseRequests.readSlotClick(registries(), tag);
            if (click != null && player instanceof ServerPlayer serverPlayer) {
                // a click on nothing with nothing on the cursor (or with shift) does not mean anything
                if (!click.item().isEmpty() || (!click.shift() && !serverPlayer.containerMenu.getCarried().isEmpty())) {
                    blockEntity.handleSlotClick(serverPlayer, click.item(), click.shift(), click.right());
                }
            }
        }
    }

    // ---- client input (the screen calls these) ----

    /** Client: a click on an item of the block (or empty space). */
    public void handleClientRequestSpecific(ItemStack stack, boolean isShiftClick, boolean isRightClick) {
        sendDataToServer(WarehouseRequests.writeSlotClick(registries(), stack, stack.getCount(), isShiftClick, isRightClick));
    }

    public void sendFiltersToServer() {
        CompoundTag tag = new CompoundTag();
        NBTHelper.writeSerializablesTo(registries(), tag, WarehouseRequests.FILTER_LIST_TAG, filters);
        sendDataToServer(tag);
    }
}
