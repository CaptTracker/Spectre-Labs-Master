/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWarehouseStockViewer.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfare.util.NBTHelper;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStockViewerBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStockViewerBlockEntity.WarehouseStockFilter;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The stock viewer sign's screen: up to {@value #MAX_FILTERS} items whose warehouse count the sign shows. The counts and the
 * saved filters reach the client with the block entity's update packet (the block entity tells this menu through
 * {@link #onFiltersChanged}); the menu only sends the edited list to the server ({@code filterList}, original key).
 * The server resets every quantity, as it counts them itself.
 */
public class WarehouseStockViewerMenu extends AutomationMenu<WarehouseStockViewerBlockEntity> implements WarehouseStockViewerBlockEntity.Viewer {
    public static final int MAX_FILTERS = 4;

    public final List<WarehouseStockFilter> filters = new ArrayList<>();

    /** Client side. */
    public WarehouseStockViewerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, WarehouseStockViewerBlockEntity.class));
    }

    public WarehouseStockViewerMenu(int containerId, Inventory inventory, WarehouseStockViewerBlockEntity viewer) {
        super(AwAutomationMenus.WAREHOUSE_STOCK_VIEWER.get(), containerId, inventory, viewer);
        filters.addAll(viewer.getFilters());
        viewer.addViewer(this);
        addPlayerSlots(inventory, 8, 88, 4); //240-8-4-4*18
    }

    @Override
    public void removed(Player who) {
        blockEntity.removeViewer(this);
        super.removed(who);
    }

    /**
     * From the block entity when its filters (or the counts in them) changed. On the client the list is replaced by the
     * block entity's and the screen rebuilt; on the server there is nothing to do.
     */
    @Override
    public void onFiltersChanged() {
        if (isClient() && !snapshot(filters).equals(snapshot(blockEntity.getFilters()))) {
            // only rebuild the screen if something differs, so an update that changes nothing does not interrupt typing
            filters.clear();
            filters.addAll(blockEntity.getFilters());
            refreshGui();
        }
    }

    private CompoundTag snapshot(List<WarehouseStockFilter> list) {
        CompoundTag tag = new CompoundTag();
        NBTHelper.writeSerializablesTo(registries(), tag, WarehouseRequests.FILTER_LIST_TAG, list);
        return tag;
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (isClient() || !isRequestAllowed() || !tag.contains(WarehouseRequests.FILTER_LIST_TAG, Tag.TAG_LIST)) {
            return;
        }
        List<WarehouseStockFilter> received = WarehouseRequests.readFilters(registries(), tag, WarehouseStockFilter::new, MAX_FILTERS);
        for (WarehouseStockFilter filter : received) {
            filter.setItem(WarehouseRequests.template(filter.getFilterItem()));
            filter.setQuantity(0); // counted by the block entity
        }
        blockEntity.setFilters(received);
    }

    /** Client: sends the edited filter list. */
    public void sendFiltersToServer() {
        CompoundTag tag = new CompoundTag();
        NBTHelper.writeSerializablesTo(registries(), tag, WarehouseRequests.FILTER_LIST_TAG, filters);
        sendDataToServer(tag);
    }
}
