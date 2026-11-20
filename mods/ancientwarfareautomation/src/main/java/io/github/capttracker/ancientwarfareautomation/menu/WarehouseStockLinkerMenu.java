/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWarehouseStockLinker.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfare.util.NBTHelper;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStockLinkerBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStockLinkerBlockEntity.WarehouseStockFilter;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The stock linker's screen: up to {@value #MAX_FILTERS} comparisons (item, sign, value) whose result drives the block's
 * redstone output. Filters and live counts reach the client with the block entity's update packet (it calls
 * {@link #onFiltersChanged}); the menu sends the edited list ({@code filterList}, original key). The server keeps each
 * item to one item, the sign inside the enum and the compare value within 0..{@value #MAX_COMPARE_VALUE}, and resets the
 * quantities (the block entity counts them).
 */
public class WarehouseStockLinkerMenu extends AutomationMenu<WarehouseStockLinkerBlockEntity> implements WarehouseStockLinkerBlockEntity.Viewer {
    public static final int MAX_FILTERS = 4;
    public static final int MAX_COMPARE_VALUE = 100_000_000;

    public final List<WarehouseStockFilter> filters = new ArrayList<>();

    /** Client side. */
    public WarehouseStockLinkerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, WarehouseStockLinkerBlockEntity.class));
    }

    public WarehouseStockLinkerMenu(int containerId, Inventory inventory, WarehouseStockLinkerBlockEntity linker) {
        super(AwAutomationMenus.WAREHOUSE_STOCK_LINKER.get(), containerId, inventory, linker);
        filters.addAll(linker.getFilters());
        linker.addViewer(this);
        addPlayerSlots(inventory, (256 - (9 * 18)) / 2, 88, 4);
    }

    @Override
    public void removed(Player who) {
        blockEntity.removeViewer(this);
        super.removed(who);
    }

    /**
     * From the block entity when its filters (or the counts in them) changed. On the client the list is replaced by the
     * block entity's and the screen rebuilt (the original kept the list it was opened with, so the counts never moved).
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
            filter.setCompareValue(Mth.clamp(filter.getCompareValue(), 0, MAX_COMPARE_VALUE));
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
