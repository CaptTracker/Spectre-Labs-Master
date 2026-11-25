/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/inventory/InventorySlotlessBasic.
 */
package io.github.capttracker.ancientwarfare.inventory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** A bounded bag of items with no slots, only a total capacity. Used by the warehouse storage. */
public class InventorySlotlessBasic {
    private final int totalSize;
    private int currentSize;
    private final ItemQuantityMap itemMap = new ItemQuantityMap();

    public InventorySlotlessBasic(int totalSize) {
        this.totalSize = totalSize;
    }

    /** Adds to the input map all items contained in this inventory. */
    public void getItems(ItemQuantityMap map) {
        map.addAll(itemMap);
    }

    public int getQuantityStored(ItemStack filter) {
        return itemMap.getCount(filter);
    }

    public int getAvailableSpaceFor(ItemStack filter) {
        return totalSize - currentSize;
    }

    public int extractItem(ItemStack filter, int amount) {
        if (amount <= 0 || filter.isEmpty()) {
            return 0;
        }
        int count = itemMap.getCount(filter);
        amount = Math.min(amount, count);
        itemMap.decreaseCount(filter, amount);
        currentSize -= amount;
        return amount;
    }

    public int insertItem(ItemStack filter, int amount) {
        if (amount <= 0 || filter.isEmpty()) {
            return 0;
        }
        if (amount > (totalSize - currentSize)) {
            amount = totalSize - currentSize;
        }
        itemMap.addCount(filter, amount);
        currentSize += amount;
        return amount;
    }

    public void readFromNBT(HolderLookup.Provider registries, CompoundTag tag) {
        itemMap.readFromNBT(registries, tag.getCompound("itemMap"));
        currentSize = itemMap.getTotalItemCount();
    }

    public CompoundTag writeToNBT(HolderLookup.Provider registries, CompoundTag tag) {
        tag.put("itemMap", itemMap.writeToNBT(registries, new CompoundTag()));
        return tag;
    }
}
