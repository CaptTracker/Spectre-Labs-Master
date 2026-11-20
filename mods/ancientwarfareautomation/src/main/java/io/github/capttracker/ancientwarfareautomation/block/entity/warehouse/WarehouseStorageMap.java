/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/warehouse2/WarehouseStorageMap.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.warehouse;

import io.github.capttracker.ancientwarfare.inventory.ItemHashEntry;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which storage blocks of one warehouse take which items: those without filters take anything, those with filters
 * only their filtered items. Kept as in the original, including its known scalability problems (rework candidates in
 * the notes): every lookup builds an {@link ItemHashEntry}, lookups create empty sets ({@code computeIfAbsent}), and
 * the destination lists are freshly allocated, in hash set order.
 */
public final class WarehouseStorageMap {

    private final Set<WarehouseStorageBlockEntity> unfilteredStorage = new HashSet<>();
    private final Set<WarehouseStorageBlockEntity> filteredStorage = new HashSet<>();
    private final Map<ItemHashEntry, Set<WarehouseStorageBlockEntity>> storageMap = new HashMap<>();

    public void addStorageTile(WarehouseStorageBlockEntity tile) {
        addTileFilters(tile, tile.getFilters());
    }

    public void removeStorageTile(WarehouseStorageBlockEntity tile) {
        removeTileFilters(tile, tile.getFilters());
    }

    public void updateTileFilters(WarehouseStorageBlockEntity tile, List<WarehouseStorageFilter> oldFilters, List<WarehouseStorageFilter> newFilters) {
        removeTileFilters(tile, oldFilters);
        addTileFilters(tile, newFilters);
    }

    public Set<WarehouseStorageBlockEntity> getFilterSetFor(ItemStack filter) {
        return getOrCreateStorageSet(new ItemHashEntry(filter));
    }

    public Set<WarehouseStorageBlockEntity> getUnFilteredSet() {
        return unfilteredStorage;
    }

    /** Every storage block, filtered ones first: the order items are taken out in. */
    public List<WarehouseStorageBlockEntity> getDestinations() {
        List<WarehouseStorageBlockEntity> out = new ArrayList<>();

        out.addAll(filteredStorage);
        out.addAll(unfilteredStorage);

        return out;
    }

    /** The storage blocks that take the item: those filtered for it first, then the unfiltered ones. */
    public List<WarehouseStorageBlockEntity> getDestinations(ItemStack filter) {
        List<WarehouseStorageBlockEntity> out = new ArrayList<>();
        ItemHashEntry key = new ItemHashEntry(filter);
        Set<WarehouseStorageBlockEntity> set = getOrCreateStorageSet(key);
        out.addAll(set);
        out.addAll(unfilteredStorage);

        return out;
    }

    private Set<WarehouseStorageBlockEntity> getOrCreateStorageSet(ItemHashEntry key) {
        return storageMap.computeIfAbsent(key, k -> new HashSet<>());
    }

    private void removeTileFilters(WarehouseStorageBlockEntity tile, List<WarehouseStorageFilter> filters) {
        if (filters.isEmpty()) {
            unfilteredStorage.remove(tile);
        } else {
            filteredStorage.remove(tile);
            for (WarehouseStorageFilter filter : filters) {
                if (filter.getHashKey() == null) {
                    continue;
                }
                getOrCreateStorageSet(filter.getHashKey()).remove(tile);
            }
        }
    }

    private void addTileFilters(WarehouseStorageBlockEntity tile, List<WarehouseStorageFilter> filters) {
        if (filters.isEmpty()) {
            unfilteredStorage.add(tile);
        } else {
            filteredStorage.add(tile);
            for (WarehouseStorageFilter filter : filters) {
                if (filter.getHashKey() == null) {
                    continue;
                }
                getOrCreateStorageSet(filter.getHashKey()).add(tile);
            }
        }
    }
}
