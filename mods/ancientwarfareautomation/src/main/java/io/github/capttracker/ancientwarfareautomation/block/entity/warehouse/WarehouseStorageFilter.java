/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/warehouse2/WarehouseStorageFilter.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.warehouse;

import io.github.capttracker.ancientwarfare.inventory.ItemHashEntry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;

import javax.annotation.Nullable;

/**
 * A whitelist entry of a storage block: the exact item (item and data components, like the warehouse's own keys) it
 * accepts. A storage block with at least one entry accepts only those items; one without accepts anything.
 */
public class WarehouseStorageFilter implements INBTSerializable<CompoundTag> {
    @Nullable
    private ItemHashEntry hashKey;
    private ItemStack item = ItemStack.EMPTY;

    public WarehouseStorageFilter() {}

    public WarehouseStorageFilter(ItemStack filter) {
        setFilterItem(filter);
    }

    public ItemStack getFilterItem() {
        return item;
    }

    public void setFilterItem(ItemStack itemStack) {
        item = itemStack;
        hashKey = item.isEmpty() ? null : new ItemHashEntry(item);
    }

    /** The map key of the item, null for an empty filter. */
    @Nullable
    ItemHashEntry getHashKey() {
        return hashKey;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("item", item.saveOptional(registries));
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        if (tag.contains("item")) {
            setFilterItem(ItemStack.parseOptional(registries, tag.getCompound("item")));
        } else {
            setFilterItem(ItemStack.EMPTY);
        }
    }
}
