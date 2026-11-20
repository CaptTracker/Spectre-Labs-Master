/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the synchItemMaps / handleChangeList code that ContainerWarehouseControl,
 * ContainerWarehouseStorage and ContainerWarehouseCraftingStation each carried a copy of.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfare.inventory.ItemHashEntry;
import io.github.capttracker.ancientwarfare.inventory.ItemQuantityMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The change-list protocol the warehouse menus use to keep a client's copy of an item map up to date (original keys:
 * {@code changeList}, a list of {@code {item, quantity}} entries, quantity 0 meaning "removed").
 *
 * <p>{@link #map()} is the copy of the map: on the server what the client has been sent so far, on the client what it
 * received. The server calls {@link #sendChanges} when its source changed; the client calls {@link #handle}.
 *
 * <p>Change: the original sent one packet with every changed entry. A client-bound custom payload is limited to 1 MiB,
 * so the entries are split over several packets of at most {@link #MAX_ENTRIES_PER_PACKET} entries.
 */
public final class ItemMapSync {
    public static final String CHANGE_LIST_TAG = "changeList";
    private static final int MAX_ENTRIES_PER_PACKET = 48;

    private final ItemQuantityMap map = new ItemQuantityMap();
    private final ItemQuantityMap scratch = new ItemQuantityMap();

    public ItemQuantityMap map() {
        return map;
    }

    /**
     * Server: compares the current content of the source (filled into a scratch map by {@code source}) with what the
     * client has and sends every difference.
     */
    public void sendChanges(HolderLookup.Provider registries, Consumer<ItemQuantityMap> source, Consumer<CompoundTag> sender) {
        scratch.clear();
        source.accept(scratch);

        List<ItemHashEntry> changed = new ArrayList<>();
        for (ItemHashEntry known : new ArrayList<>(map.keySet())) {
            if (map.getCount(known) != scratch.getCount(known)) {
                changed.add(known);
            }
        }
        for (ItemHashEntry entry : scratch.keySet()) {
            if (!map.contains(entry)) {
                changed.add(entry);
            }
        }
        if (changed.isEmpty()) {
            return;
        }

        ListTag list = new ListTag();
        for (ItemHashEntry entry : changed) {
            int qty = scratch.getCount(entry);
            list.add(scratch.writeEntryToNBT(registries, entry));
            if (qty == 0) {
                map.remove(entry);
            } else {
                map.put(entry, qty);
            }
            if (list.size() >= MAX_ENTRIES_PER_PACKET) {
                flush(list, sender);
                list = new ListTag();
            }
        }
        if (!list.isEmpty()) {
            flush(list, sender);
        }
    }

    private static void flush(ListTag list, Consumer<CompoundTag> sender) {
        CompoundTag tag = new CompoundTag();
        tag.put(CHANGE_LIST_TAG, list);
        sender.accept(tag);
    }

    /** Client: applies a received change list to the map. @return whether the tag was a change list */
    public boolean handle(HolderLookup.Provider registries, CompoundTag tag) {
        if (!tag.contains(CHANGE_LIST_TAG, Tag.TAG_LIST)) {
            return false;
        }
        ListTag list = tag.getList(CHANGE_LIST_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            map.putEntryFromNBT(registries, list.getCompound(i));
        }
        return true;
    }
}
