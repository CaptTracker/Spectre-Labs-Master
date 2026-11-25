/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/warehouse2/WarehouseDebugger.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.warehouse;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.inventory.ItemHashEntry;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.command.WarehouseCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * The warehouse debugger: an auditor of the item counts a warehouse reports. {@code /awwarehouse startdebug x y z}
 * starts tracking a warehouse: from then on the debugger follows every change of the warehouse's item cache
 * ({@link WarehouseBlockEntity#changeCachedQuantity}) in a count of its own and, once a second, compares the two and logs
 * every difference. Nothing happens for warehouses that are not tracked.
 *
 * <p>Installed with {@code WarehouseCommand.setDebugger(WarehouseDebugger.COMMAND)} and ticked by
 * {@link #onLevelTick} (both done by {@code WarehouseSetup.registerEvents}). The data is in memory only and dropped
 * when the server stops.
 */
public final class WarehouseDebugger {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<ResourceKey<Level>, WarehouseDebuggerData> DATA = new HashMap<>();

    /** What {@code /awwarehouse} talks to. */
    public static final WarehouseCommand.IWarehouseDebugger COMMAND = new WarehouseCommand.IWarehouseDebugger() {
        @Override
        public void startTrackingWarehouse(ServerLevel level, BlockPos pos) {
            WarehouseDebugger.startTrackingWarehouse(level, pos);
        }

        @Override
        public void stopTrackingWarehouse(ServerLevel level, BlockPos pos) {
            WarehouseDebugger.stopTrackingWarehouse(level, pos);
        }
    };

    private WarehouseDebugger() {}

    // ************************************** EVENTS ***************************************//

    /** End of a level tick (1.12.2: {@code WorldTickEvent}, END phase). */
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        WarehouseDebuggerData data = DATA.get(level.dimension());
        if (data != null) {
            tickData(level, data);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        DATA.clear();
    }

    private static void tickData(Level world, WarehouseDebuggerData data) {
        if (data.decreaseAndCheckCooldown()) {
            reviewInventories(world, data);
        }
    }

    // ************************************** REVIEW ***************************************//

    private static void reviewInventories(Level world, WarehouseDebuggerData data) {
        Set<BlockPos> invalidWarehouses = new HashSet<>();
        for (BlockPos warehousePos : new HashSet<>(data.getTrackedWarehouses())) {
            if (world.isLoaded(warehousePos)) {
                if (!(world.getBlockEntity(warehousePos) instanceof WarehouseBlockEntity warehouse)) {
                    invalidWarehouses.add(warehousePos);
                    continue;
                }

                reviewInventory(warehouse, data.getItemCounts(warehousePos));
            }
        }

        invalidWarehouses.forEach(data::removeTrackedWarehouse);
    }

    private static void reviewInventory(WarehouseBlockEntity warehouse, Map<ItemStack, Integer> debugData) {
        HashMap<ItemStack, Integer> copy = new HashMap<>(debugData);
        for (Map.Entry<ItemHashEntry, Integer> entry : warehouse.getItemCache().getItemCounts().entrySet()) {
            reviewStackCount(warehouse.getBlockPos(), debugData, copy, entry);
        }

        for (Map.Entry<ItemStack, Integer> entry : copy.entrySet()) {
            LOGGER.info("Warehouse {} misses {} of {} that is in debugData", warehouse.getBlockPos(), entry.getValue(), describe(entry.getKey()));
            debugData.remove(entry.getKey());
        }
    }

    private static void reviewStackCount(BlockPos warehousePos, Map<ItemStack, Integer> debugData, Map<ItemStack, Integer> debugDataCopy,
                                         Map.Entry<ItemHashEntry, Integer> warehouseEntry) {
        ItemStack warehouseStack = warehouseEntry.getKey().getItemStack();
        int warehouseCount = warehouseEntry.getValue();

        Iterator<Map.Entry<ItemStack, Integer>> it = debugDataCopy.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ItemStack, Integer> entry = it.next();
            if (InventoryTools.doItemStacksMatch(warehouseStack, entry.getKey())) {
                if (warehouseCount != entry.getValue()) {
                    LOGGER.info("Warehouse {} has {} {} of {} than what's tracked in debug data", warehousePos,
                            Math.abs(entry.getValue() - warehouseCount), warehouseCount > entry.getValue() ? "more" : "less", describe(warehouseStack));
                    debugData.put(entry.getKey(), warehouseCount);
                }
                it.remove();
                return;
            }
        }

        LOGGER.info("Warehouse {} has {} of {} which is missing in debug data", warehousePos, warehouseCount, describe(warehouseStack));
        debugData.put(warehouseStack, warehouseCount);
    }

    /** The item id and its component changes: enough to tell items apart in a log. */
    private static String describe(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + " " + stack.getComponentsPatch();
    }

    // ************************************** API ***************************************//

    public static void startTrackingWarehouse(Level world, BlockPos pos) {
        if (!world.isLoaded(pos)) {
            return;
        }
        WarehouseDebuggerData data = getOrCreateData(world);
        data.addTrackedWarehouse(pos);
        initializeItemCounts(world, pos, data);
    }

    private static void initializeItemCounts(Level world, BlockPos pos, WarehouseDebuggerData debugData) {
        if (world.getBlockEntity(pos) instanceof WarehouseBlockEntity wh) {
            Map<ItemStack, Integer> warehouseItems = new HashMap<>();
            for (Map.Entry<ItemHashEntry, Integer> itemCount : wh.getItemCache().getItemCounts().entrySet()) {
                warehouseItems.put(itemCount.getKey().getItemStack().copy(), itemCount.getValue());
            }
            debugData.setItemCounts(pos, warehouseItems);
        }
    }

    public static void stopTrackingWarehouse(Level world, BlockPos pos) {
        WarehouseDebuggerData data = getData(world);
        if (data != null) {
            data.removeTrackedWarehouse(pos);
        }
    }

    public static void removeItem(Level world, BlockPos pos, ItemStack stack, int count) {
        WarehouseDebuggerData data = getData(world);
        if (data != null) {
            data.removeItem(pos, stack, count);
        }
    }

    public static void addItem(Level world, BlockPos pos, ItemStack stack, int count) {
        WarehouseDebuggerData data = getData(world);
        if (data != null) {
            data.addItem(pos, stack, count);
        }
    }

    /** Called when the bounds of a warehouse changed: starts the tracked counts again from what the cache holds. */
    public static void reinitializeItemCounts(Level world, BlockPos pos) {
        WarehouseDebuggerData data = getData(world);
        if (data != null && data.isTrackingWarehouse(pos)) {
            data.removeTrackedWarehouse(pos);
            data.addTrackedWarehouse(pos);
            initializeItemCounts(world, pos, data);
        }
    }

    /** Called by the warehouse for every change of its item cache; does nothing while the level has no tracked warehouse. */
    public static void changeItemQuantity(Level world, BlockPos pos, ItemStack stack, int change) {
        WarehouseDebuggerData data = getData(world);
        if (data == null) {
            return;
        }
        if (change > 0) {
            data.addItem(pos, stack, change);
        } else if (change < 0) {
            data.removeItem(pos, stack, -change);
        }
    }

    @Nullable
    private static WarehouseDebuggerData getData(Level world) {
        return DATA.get(world.dimension());
    }

    private static WarehouseDebuggerData getOrCreateData(Level world) {
        return DATA.computeIfAbsent(world.dimension(), dimension -> new WarehouseDebuggerData());
    }
}
