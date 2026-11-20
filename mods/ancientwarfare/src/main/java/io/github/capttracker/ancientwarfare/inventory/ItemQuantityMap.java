/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/inventory/ItemQuantityMap.
 */
package io.github.capttracker.ancientwarfare.inventory;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Counts of items, keyed by {@link ItemHashEntry} (item plus components, quantity ignored). */
public class ItemQuantityMap {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<ItemHashEntry, Integer> map = new HashMap<>();

    /** Not a PUT operation: merges quantities instead of overwriting. */
    public void addAll(ItemQuantityMap incoming) {
        for (Map.Entry<ItemHashEntry, Integer> entry : incoming.map.entrySet()) {
            map.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    /** Removes the given counts from this map; entries that reach zero are removed as well. */
    public void removeAll(ItemQuantityMap toRemove) {
        for (Map.Entry<ItemHashEntry, Integer> removal : toRemove.map.entrySet()) {
            ItemHashEntry entry = removal.getKey();
            Integer currentCount = map.get(entry);
            if (currentCount == null) {
                // The original threw a NullPointerException here; nothing to remove is not an error.
                continue;
            }
            int countToRemove = removal.getValue();

            if (countToRemove >= currentCount) {
                remove(entry);
            } else {
                map.put(entry, currentCount - countToRemove);
            }
        }
    }

    public int getCount(ItemHashEntry entry) {
        return map.getOrDefault(entry, 0);
    }

    public int getCount(ItemStack item) {
        return getCount(new ItemHashEntry(item));
    }

    public void addCount(ItemStack item, int count) {
        addCount(new ItemHashEntry(item), count);
    }

    private void addCount(ItemHashEntry entry, int count) {
        map.merge(entry, count, Integer::sum);
    }

    public void decreaseCount(ItemStack item, int count) {
        decreaseCount(new ItemHashEntry(item), count);
    }

    private void decreaseCount(ItemHashEntry entry, int count) {
        Integer current = map.get(entry);
        if (current != null) {
            int itemCount = current - count;
            if (itemCount <= 0) {
                map.remove(entry);
            } else {
                map.put(entry, itemCount);
            }
        }
    }

    public void remove(ItemHashEntry entry) {
        map.remove(entry);
    }

    public void put(ItemStack item, int count) {
        put(new ItemHashEntry(item), count);
    }

    public void put(ItemHashEntry wrap, int count) {
        map.put(wrap, count);
    }

    public void clear() {
        this.map.clear();
    }

    public Set<ItemHashEntry> keySet() {
        return map.keySet();
    }

    public boolean contains(ItemHashEntry entry) {
        return map.containsKey(entry);
    }

    public boolean contains(ItemStack item) {
        return contains(new ItemHashEntry(item));
    }

    /**
     * The most compact set of item stacks that can represent the contents of this map. May return
     * several stacks of the same item if the quantity is greater than the max stack size.
     */
    public NonNullList<ItemStack> getItems() {
        NonNullList<ItemStack> items = NonNullList.create();
        for (Map.Entry<ItemHashEntry, Integer> entry : map.entrySet()) {
            int qty = entry.getValue();
            while (qty > 0) {
                ItemStack outStack = entry.getKey().getItemStack().copy();
                outStack.setCount(Math.min(qty, outStack.getMaxStackSize()));
                qty -= outStack.getCount();
                items.add(outStack);
            }
        }
        return items;
    }

    public Map<ItemHashEntry, Integer> getItemCounts() {
        return map;
    }

    public void readFromNBT(HolderLookup.Provider registries, CompoundTag tag) {
        ListTag entryList = tag.getList("entryList", Tag.TAG_COMPOUND);
        for (int i = 0; i < entryList.size(); i++) {
            putEntryFromNBT(registries, entryList.getCompound(i));
        }
    }

    public void putEntryFromNBT(HolderLookup.Provider registries, CompoundTag entryTag) {
        CompoundTag itemTag = entryTag.getCompound("item");
        ItemHashEntry entry = ItemHashEntry.readFromNBT(registries, itemTag);
        if (!entry.getItemStack().isEmpty()) {
            int qty = entryTag.getInt("quantity");
            if (qty == 0) { // when deserializing from NBT just remove all entries with 0
                map.remove(entry);
            } else {
                map.put(entry, qty);
            }
        } else {
            LOGGER.warn("Unable to read item from NBT into quantity map, probably no longer exists. {}", itemTag);
        }
    }

    public CompoundTag writeToNBT(HolderLookup.Provider registries, CompoundTag tag) {
        ListTag entryList = new ListTag();
        for (ItemHashEntry entry : this.keySet()) {
            entryList.add(writeEntryToNBT(registries, entry));
        }
        tag.put("entryList", entryList);
        return tag;
    }

    public CompoundTag writeEntryToNBT(HolderLookup.Provider registries, ItemHashEntry entry) {
        CompoundTag entryTag = new CompoundTag();
        entryTag.put("item", entry.writeToNBT(registries));
        entryTag.putInt("quantity", getCount(entry));
        return entryTag;
    }

    public int getTotalItemCount() {
        return map.values().stream().mapToInt(i -> i).sum();
    }
}
