/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWarehouseInterface.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfare.util.NBTHelper;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseInterfaceBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseInterfaceFilter;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * The warehouse interface block's screen: its nine item slots, the filter list (at most {@value #MAX_FILTERS} items with a
 * quantity each) and the player's inventory. Protocol (original key): {@code filterList} both ways. The server keeps the
 * filter items to one item each and the quantities within 0..{@value #MAX_QUANTITY}.
 */
public class WarehouseInterfaceMenu extends AutomationMenu<WarehouseInterfaceBlockEntity> implements WarehouseInterfaceBlockEntity.Viewer {
    public static final int MAX_FILTERS = 9;
    public static final int MAX_QUANTITY = 100_000;

    public List<WarehouseInterfaceFilter> filters = new ArrayList<>();

    /** Client side. */
    public WarehouseInterfaceMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, WarehouseInterfaceBlockEntity.class));
    }

    public WarehouseInterfaceMenu(int containerId, Inventory inventory, WarehouseInterfaceBlockEntity warehouseInterface) {
        super(AwAutomationMenus.WAREHOUSE_INTERFACE.get(), containerId, inventory, warehouseInterface);
        for (int i = 0; i < 9; i++) {
            addSlot(new SlotItemHandler(warehouseInterface.getInventory(), i, (i % 3) * 18 + 8 + 3 * 18, (i / 3) * 18 + 8 + 80 + 8));
        }
        warehouseInterface.addViewer(this);
        filters.addAll(warehouseInterface.getFilters());
        addPlayerSlots(inventory, 8, 8 + 8 + 3 * 18 + 80 + 8, 4);
    }

    @Override
    public void removed(Player who) {
        blockEntity.removeViewer(this);
        super.removed(who);
    }

    private CompoundTag filtersTag() {
        CompoundTag tag = new CompoundTag();
        NBTHelper.writeSerializablesTo(registries(), tag, WarehouseRequests.FILTER_LIST_TAG, filters);
        return tag;
    }

    @Override
    protected void sendInitialState() {
        sendDataToClient(filtersTag());
    }

    /** Client: sends the filter list (call whenever it changed). */
    public void sendFiltersToServer() {
        sendDataToServer(filtersTag());
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (!tag.contains(WarehouseRequests.FILTER_LIST_TAG, Tag.TAG_LIST)) {
            return;
        }
        if (isClient()) {
            this.filters = NBTHelper.deserializeListFrom(registries(), tag, WarehouseRequests.FILTER_LIST_TAG, WarehouseInterfaceFilter::new);
            refreshGui();
        } else if (isRequestAllowed()) {
            List<WarehouseInterfaceFilter> received = WarehouseRequests.readFilters(registries(), tag, WarehouseInterfaceFilter::new, MAX_FILTERS);
            for (WarehouseInterfaceFilter filter : received) {
                filter.setFilterItem(WarehouseRequests.template(filter.getFilterItem()));
                filter.setFilterQuantity(Mth.clamp(filter.getFilterQuantity(), 0, MAX_QUANTITY));
            }
            blockEntity.setFilters(received);
        }
    }

    /** Server, from the block entity: its filters changed. */
    @Override
    public void onInterfaceFiltersChanged() {
        filters.clear();
        filters.addAll(blockEntity.getFilters());
        sendInitData();
    }
}
