/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/warehouse2/WarehouseDebuggerData.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.warehouse;

import io.github.capttracker.ancientwarfare.util.InventoryTools;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * What the warehouse debugger tracks in one level: the warehouses and, for each, the item counts the debugger worked
 * out from the changes it saw. In memory only: the 1.12.2 saved data never wrote anything, so nothing is lost by it
 * not being {@code SavedData} any more.
 */
final class WarehouseDebuggerData {
    private static final int TRACKING_INTERVAL = 20;

    private final Set<BlockPos> trackedWarehouses = new HashSet<>();
    private final Map<BlockPos, Map<ItemStack, Integer>> itemCounts = new HashMap<>();

    private int currentCooldown = 0;

    boolean decreaseAndCheckCooldown() {
        currentCooldown--;
        if (currentCooldown <= 0) {
            currentCooldown = TRACKING_INTERVAL;
            return true;
        }
        return false;
    }

    Set<BlockPos> getTrackedWarehouses() {
        return trackedWarehouses;
    }

    void removeTrackedWarehouse(BlockPos warehousePos) {
        trackedWarehouses.remove(warehousePos);
        itemCounts.remove(warehousePos);
    }

    Map<ItemStack, Integer> getItemCounts(BlockPos warehousePos) {
        return itemCounts.computeIfAbsent(warehousePos, pos -> new HashMap<>());
    }

    void addTrackedWarehouse(BlockPos pos) {
        trackedWarehouses.add(pos);
    }

    void setItemCounts(BlockPos pos, Map<ItemStack, Integer> itemCounts) {
        this.itemCounts.put(pos, itemCounts);
    }

    void removeItem(BlockPos warehousePos, ItemStack stack, int count) {
        if (!trackedWarehouses.contains(warehousePos)) {
            return;
        }

        Iterator<Map.Entry<ItemStack, Integer>> it = getItemCounts(warehousePos).entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ItemStack, Integer> entry = it.next();
            if (InventoryTools.doItemStacksMatch(entry.getKey(), stack)) {
                entry.setValue(entry.getValue() - count);
                if (entry.getValue() <= 0) {
                    it.remove();
                }
                break;
            }
        }
    }

    void addItem(BlockPos warehousePos, ItemStack stack, int count) {
        if (!trackedWarehouses.contains(warehousePos)) {
            return;
        }

        Map<ItemStack, Integer> counts = getItemCounts(warehousePos);
        for (Map.Entry<ItemStack, Integer> entry : counts.entrySet()) {
            if (InventoryTools.doItemStacksMatch(entry.getKey(), stack)) {
                entry.setValue(entry.getValue() + count);
                return;
            }
        }
        counts.put(stack.copy(), count);
    }

    boolean isTrackingWarehouse(BlockPos pos) {
        return trackedWarehouses.contains(pos);
    }
}
