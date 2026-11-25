/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/warehouse2/TileWarehouseBase and TileWarehouse (one class now; the
 * base had no other subclass).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.warehouse;

import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.inventory.ItemQuantityMap;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfare.util.InventoryTools.ComparatorItemHashEntry.SortOrder;
import io.github.capttracker.ancientwarfare.util.InventoryTools.ComparatorItemHashEntry.SortType;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseInterfaceBlockEntity.InterfaceEmptyRequest;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseInterfaceBlockEntity.InterfaceFillRequest;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.BoundedWorksiteBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.IWorksiteAction;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The warehouse control block: a bounded worksite (the player sets a box of up to 3 blocks height; width 5, 9 or 16
 * with the bounds upgrades) that owns the storage, interface, stock viewer and stock linker blocks attached to it
 * ({@link ControlledBlockEntity}) and keeps the sum of their items, the item cache ({@link #getItems}).
 *
 * <p><b>Items</b>: all counts are {@code ItemHashEntry} (item plus data components) to quantity. The cache is server
 * authoritative and not saved; it is rebuilt when the storage blocks attach (each adds its items). Taking and
 * putting items, by the player in the screen ({@link #handleSlotClick}), by the crafting station
 * ({@link #getItemHandler()}) or by the interface blocks (worksite actions), goes through the
 * {@link WarehouseStorageMap}, which routes by the storage blocks' filters.
 *
 * <p><b>Interface work</b>: the worksite loop ({@code WorksiteBlockEntity#update}) asks {@link #getNextAction()}; if an
 * interface needs emptying that is done first, else one that needs filling. Each action costs a quarter of the
 * energy of a normal worksite action and moves at most one stack. Only crafting workers (work type CRAFTING) power
 * the block, or torque and Forge Energy (worksite base).
 *
 * <p><b>Lifecycle</b>: the first worksite update scans the bounds (widened by one block horizontally, for stock
 * viewers) for blocks without a controller; blocks that find the controller first attach themselves. When the block is
 * removed ({@link #setRemoved()}, break or unload) every attached block is detached. The 1.12.2 {@code invalidate}
 * skipped the worksite base cleanup; this one calls {@code super}. It also detaches the blocks it knows instead of
 * scanning (and loading) the chunks of its bounds.
 *
 * <p><b>Menus</b> (GUI slice): the control menu registers with {@link #addViewer}, the crafting station menu with
 * {@link #addCraftingViewer}; both are told when the content changed ({@link Viewer}) and then diff
 * {@link #getItems} themselves. The click is handled by {@link #handleSlotClick}.
 *
 * <p>Saved: the worksite base data (bounds, upgrades, torque), {@code stockLinkerPositions}, {@code sortOrder} and
 * {@code sortType}; the sort is also sent in the update tag.
 */
public class WarehouseBlockEntity extends BoundedWorksiteBlockEntity implements IControllerTile {
    private static final String STOCK_LINKER_POSITIONS_TAG = "stockLinkerPositions";

    /** An open menu showing the warehouse content: told (server side) that the content changed. */
    public interface Viewer {
        void onWarehouseInventoryUpdated();
    }

    private boolean init;
    private boolean shouldRecount;

    private final Set<WarehouseStockViewerBlockEntity> stockViewers = new HashSet<>();
    private Set<BlockPos> stockLinkerPositions = new HashSet<>();
    private final Set<WarehouseInterfaceBlockEntity> interfaceTiles = new HashSet<>();
    private final Set<WarehouseStorageBlockEntity> storageTiles = new HashSet<>();

    /*
     * Interfaces that need filling, AND there are items available to fill.
     * Anytime storage block inventories are updated, this list needs to be rechecked
     * to make sure items are still available
     */
    private final Set<WarehouseInterfaceBlockEntity> interfacesToFill = new HashSet<>();

    /*
     * Interfaces that have an excess of items or non-matching items will be in this set
     */
    private final Set<WarehouseInterfaceBlockEntity> interfacesToEmpty = new HashSet<>();

    protected WarehouseStorageMap storageMap = new WarehouseStorageMap();
    protected ItemQuantityMap cachedItemMap = new ItemQuantityMap();

    private final Set<Viewer> viewers = new HashSet<>();
    private final Set<Viewer> craftingViewers = new HashSet<>();

    private SortType sortType = SortType.NAME;
    private SortOrder sortOrder = SortOrder.DESCENDING;

    public WarehouseBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.WAREHOUSE_CONTROL_BE.get(), pos, state);
    }

    /**
     * SERVER ONLY
     *
     * @return max allowed storage by item quantity
     */
    public int getMaxStorage() {
        int max = 0;
        for (WarehouseStorageBlockEntity t : storageTiles) {
            max += t.getStorageAdditionSize();
        }
        return max;
    }

    @Override
    public void onBoundsAdjusted() {
        BlockPos max = getWorkBoundsMax();
        setWorkBoundsMax(max.above(getWorkBoundsMin().getY() + getBoundsMaxHeight() - max.getY()));
        this.interfacesToEmpty.clear();
        this.interfacesToFill.clear();
        for (WarehouseInterfaceBlockEntity i : interfaceTiles) {
            i.setController(null);
        }
        this.interfaceTiles.clear();
        for (WarehouseStockViewerBlockEntity i : stockViewers) {
            i.setController(null);
        }
        this.stockViewers.clear();
        for (WarehouseStorageBlockEntity i : storageTiles) {
            i.setController(null);
        }
        this.storageTiles.clear();

        storageMap = new WarehouseStorageMap();
        cachedItemMap.clear();

        scanForInitialTiles();

        if (level != null) {
            WarehouseDebugger.reinitializeItemCounts(level, worldPosition);
        }
    }

    @Override
    public boolean userAdjustableBlocks() {
        return false;
    }

    @Override
    public int getBoundsMaxHeight() {
        return 3;
    }

    /*
     * Sets the height of the bounds when they are FIRST set (the box is always getBoundsMaxHeight() high).
     */
    @Override
    protected void onBoundsSet() {
        setWorkBoundsMax(getWorkBoundsMax().above(getWorkBoundsMin().getY() + getBoundsMaxHeight() - getWorkBoundsMax().getY()));
        BlockTools.notifyBlockUpdate(this);
    }

    // ************************************** LIFECYCLE ***************************************//

    @Override
    public void setRemoved() {
        for (WarehouseStorageBlockEntity tile : storageTiles) {
            tile.setController(null);
        }
        for (WarehouseInterfaceBlockEntity tile : interfaceTiles) {
            tile.setController(null);
        }
        for (WarehouseStockViewerBlockEntity tile : stockViewers) {
            tile.setController(null);
        }
        super.setRemoved();
    }

    @Override
    protected final void updateWorksite() {
        if (!init) {
            scanForInitialTiles();
        }
        if (shouldRecount) {
            shouldRecount = false;
            recountInventory();
        }
    }

    /** Attaches every controlled block in the bounds (widened by one block horizontally) that has no controller yet. */
    private void scanForInitialTiles() {
        if (level == null) {
            return;
        }
        BlockPos max = getWorkBoundsMax();
        BlockPos min = getWorkBoundsMin();
        List<BlockEntity> tiles = WarehouseScan.loadedTilesInArea(level, min.getX() - 1, min.getY(), min.getZ() - 1, max.getX() + 1, max.getY(), max.getZ() + 1);
        for (BlockEntity te : tiles) {
            if (te instanceof IControlledTile controlled && controlled.getController().isEmpty() && controlled.isValidController(this)) {
                addControlledTile(controlled);
            }
        }
        init = true;
    }

    /** Asks for the item cache to be rebuilt from the storage blocks at the next update (nothing calls it in the original). */
    public void requestRecount() {
        shouldRecount = true;
    }

    private void recountInventory() {
        cachedItemMap.clear();
        for (WarehouseStorageBlockEntity tile : storageTiles) {
            tile.addItems(cachedItemMap);
        }
    }

    // ************************************** CONTROLLED BLOCKS ***************************************//

    @Override
    public final void addControlledTile(IControlledTile tile) {
        if (tile instanceof WarehouseStorageBlockEntity storage) {
            addStorageTile(storage);
        } else if (tile instanceof WarehouseInterfaceBlockEntity warehouseInterface) {
            addInterfaceTile(warehouseInterface);
        } else if (tile instanceof WarehouseStockViewerBlockEntity viewer) {
            addStockViewer(viewer);
        } else if (tile instanceof WarehouseStockLinkerBlockEntity linker) {
            addStockLinker(linker);
        }
    }

    @Override
    public final void removeControlledTile(IControlledTile tile) {
        if (tile instanceof WarehouseStorageBlockEntity storage) {
            removeStorageTile(storage);
        } else if (tile instanceof WarehouseInterfaceBlockEntity warehouseInterface) {
            removeInterfaceTile(warehouseInterface);
        } else if (tile instanceof WarehouseStockViewerBlockEntity viewer) {
            removeStockViewer(viewer);
        } else if (tile instanceof WarehouseStockLinkerBlockEntity linker) {
            removeStockLinker(linker);
        }
    }

    private boolean isClientSide() {
        return level == null || level.isClientSide;
    }

    private void addStorageTile(WarehouseStorageBlockEntity tile) {
        if (isClientSide()) {
            return;
        }
        if (!storageTiles.contains(tile)) {
            tile.setController(this);
            storageTiles.add(tile);
            storageMap.addStorageTile(tile);
            tile.addItems(cachedItemMap);
        }
    }

    private void removeStorageTile(WarehouseStorageBlockEntity tile) {
        ItemQuantityMap iqm = new ItemQuantityMap();
        tile.addItems(iqm);
        this.cachedItemMap.removeAll(iqm);
        storageTiles.remove(tile);
        storageMap.removeStorageTile(tile);
        updateViewers();
    }

    private void addInterfaceTile(WarehouseInterfaceBlockEntity tile) {
        if (isClientSide()) {
            return;
        }
        if (!interfaceTiles.contains(tile)) {
            interfaceTiles.add(tile);
            tile.setController(this);
            if (!tile.getEmptyRequests().isEmpty()) {
                interfacesToEmpty.add(tile);
            }
            if (!tile.getFillRequests().isEmpty()) {
                interfacesToFill.add(tile);
            }
        }
    }

    private void removeInterfaceTile(WarehouseInterfaceBlockEntity tile) {
        interfaceTiles.remove(tile);
        interfacesToFill.remove(tile);
        interfacesToEmpty.remove(tile);
    }

    /** Called by an interface block whenever its requests changed (its slots or filters). */
    public final void onIterfaceInventoryChanged(WarehouseInterfaceBlockEntity tile) {
        if (isClientSide()) {
            return;
        }
        interfacesToFill.remove(tile);
        interfacesToEmpty.remove(tile);
        if (!tile.getEmptyRequests().isEmpty()) {
            interfacesToEmpty.add(tile);
        }
        if (!tile.getFillRequests().isEmpty()) {
            interfacesToFill.add(tile);
        }
    }

    /** Called by a storage block whenever its filter list changed. */
    public final void onStorageFilterChanged(WarehouseStorageBlockEntity tile, List<WarehouseStorageFilter> oldFilters, List<WarehouseStorageFilter> newFilters) {
        if (isClientSide()) {
            return;
        }
        storageMap.updateTileFilters(tile, oldFilters, newFilters);
    }

    private void addStockViewer(WarehouseStockViewerBlockEntity viewer) {
        if (isClientSide()) {
            return;
        }
        stockViewers.add(viewer);
        viewer.setController(this);
        viewer.onWarehouseInventoryUpdated();
    }

    private void removeStockViewer(WarehouseStockViewerBlockEntity tile) {
        stockViewers.remove(tile);
    }

    private void addStockLinker(WarehouseStockLinkerBlockEntity linker) {
        if (isClientSide()) {
            return;
        }
        stockLinkerPositions.add(linker.getBlockPos());
        linker.setController(this);
        linker.onWarehouseInventoryUpdated();
    }

    private void removeStockLinker(WarehouseStockLinkerBlockEntity tile) {
        stockLinkerPositions.remove(tile.getBlockPos());
    }

    @Override
    public BlockPos getControllerPosition() {
        return worldPosition;
    }

    // ************************************** VIEWERS ***************************************//

    public final void addViewer(Viewer viewer) {
        if (isClientSide()) {
            return;
        }
        viewers.add(viewer);
    }

    public final void addCraftingViewer(Viewer viewer) {
        if (isClientSide()) {
            return;
        }
        craftingViewers.add(viewer);
    }

    public final void removeViewer(Viewer viewer) {
        viewers.remove(viewer);
    }

    public final void removeCraftingViewer(Viewer viewer) {
        craftingViewers.remove(viewer);
    }

    /** Tells the menus, the stock viewers and the stock linkers (the loaded ones) that the content changed. */
    public final void updateViewers() {
        for (Viewer viewer : viewers) {
            viewer.onWarehouseInventoryUpdated();
        }
        for (WarehouseStockViewerBlockEntity viewer : stockViewers) {
            viewer.onWarehouseInventoryUpdated();
        }
        for (BlockPos linkerPos : stockLinkerPositions) {
            if (level != null && level.isLoaded(linkerPos) && level.getBlockEntity(linkerPos) instanceof WarehouseStockLinkerBlockEntity linker) {
                linker.onWarehouseInventoryUpdated();
            }
        }
        for (Viewer viewer : craftingViewers) {
            viewer.onWarehouseInventoryUpdated();
        }
    }

    // ************************************** ITEMS ***************************************//

    /** Adds the count of every item of the warehouse to the map. */
    public final void getItems(ItemQuantityMap map) {
        map.addAll(cachedItemMap);
    }

    /** Package access for the debugger: the live item cache. Do not change it. */
    ItemQuantityMap getItemCache() {
        return cachedItemMap;
    }

    public final void clearItemCache() {
        cachedItemMap.clear();
    }

    public final void addItemsToCache(ItemQuantityMap map) {
        cachedItemMap.addAll(map);
    }

    /** Changes the cached count of an item (positive: added to storage, negative: taken out) and tells the viewers. */
    public void changeCachedQuantity(ItemStack filter, int change) {
        if (change > 0) {
            cachedItemMap.addCount(filter, change);
        } else {
            cachedItemMap.decreaseCount(filter, -change);
        }
        updateViewers();

        if (level != null) {
            WarehouseDebugger.changeItemQuantity(level, worldPosition, filter, change);
        }
    }

    public int getCountOf(ItemStack layoutStack) {
        return cachedItemMap.getCount(layoutStack);
    }

    /**
     * Takes up to {@code i} of the item out of the storage blocks (on the client only the cache is changed, as a
     * prediction until the next update from the server).
     */
    public void decreaseCountOf(ItemStack layoutStack, int i) {
        if (level != null && level.isClientSide) {
            cachedItemMap.decreaseCount(layoutStack, i);
            return;
        }
        List<WarehouseStorageBlockEntity> dest = storageMap.getDestinations();
        int found;
        for (WarehouseStorageBlockEntity tile : dest) {
            found = tile.getQuantityStored(layoutStack);
            if (found > 0) {
                if (found > i) {
                    found = i;
                }
                i -= found;
                tile.extractItem(layoutStack, found);
                cachedItemMap.decreaseCount(layoutStack, found);
                if (i <= 0) {
                    break;
                }
            }
        }
        updateViewers();
    }

    /**
     * Puts as much of the stack as fits into the storage blocks. Consumes what it takes from the stack.
     *
     * @return what did not fit ({@link ItemStack#EMPTY} if everything did)
     */
    public ItemStack tryAdd(ItemStack stack) {
        List<WarehouseStorageBlockEntity> destinations = storageMap.getDestinations(stack);
        for (WarehouseStorageBlockEntity tile : destinations) {
            int moved = tile.insertItem(stack, stack.getCount());
            changeCachedQuantity(stack.copyWithCount(1), moved);
            stack.shrink(moved);
            if (stack.getCount() <= 0) {
                break;
            }
        }
        if (stack.getCount() <= 0) {
            return ItemStack.EMPTY;
        }
        return stack;
    }

    // ************************************** PLAYER CLICKS ***************************************//

    /**
     * A click on an item in the warehouse screen (server side; the menu calls it after validating the request).
     * With an item on the cursor (and no shift) the cursor stack is put into the warehouse, otherwise the clicked item
     * is taken out: left click one stack, right click half a stack, shift-right click one more than the cursor holds,
     * shift-left click straight into the player's inventory.
     *
     * @param filter the clicked item, as the client sent it
     */
    public void handleSlotClick(Player player, ItemStack filter, boolean shiftClick, boolean rightClick) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ItemStack carried = serverPlayer.containerMenu.getCarried();
        if (!shiftClick && !carried.isEmpty()) {
            tryAddItem(serverPlayer, carried);
        } else if (!filter.isEmpty()) {
            tryGetItem(serverPlayer, filter, shiftClick, rightClick);
        }
    }

    private void tryAddItem(ServerPlayer player, ItemStack cursorStack) {
        if (cursorStack.isEmpty()) {
            return;
        }
        ItemStack result = tryAddItem(cursorStack, cursorStack.getCount());
        if (result.getCount() != cursorStack.getCount()) {
            player.containerMenu.setCarried(result);
            player.containerMenu.broadcastChanges();
        }
    }

    private ItemStack tryAddItem(ItemStack stack, int count) {
        List<WarehouseStorageBlockEntity> destinations = storageMap.getDestinations(stack);
        int addedTotal = 0;
        for (WarehouseStorageBlockEntity tile : destinations) {
            int moved = tile.insertItem(stack, count - addedTotal);
            addedTotal += moved;
            changeCachedQuantity(stack, moved);
            updateViewers();
            if (addedTotal >= count) {
                return ItemStack.EMPTY;
            }
        }

        ItemStack result = stack.copy();
        result.shrink(addedTotal);
        return result;
    }

    private void tryGetItem(ServerPlayer player, ItemStack filter, boolean shiftClick, boolean rightClick) {
        int stackSize = 0;
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            if (shiftClick && rightClick) {
                stackSize = carried.getCount();
            }
            ItemStack comparableStack = carried.copyWithCount(filter.getCount());
            if (!ItemStack.matches(filter, comparableStack)) {
                return;
            }
        }

        List<WarehouseStorageBlockEntity> destinations = storageMap.getDestinations();
        int toMoveMax = filter.getMaxStackSize();
        if (rightClick && (toMoveMax > 1)) {
            if (shiftClick) {
                toMoveMax = Math.min(stackSize + 1, toMoveMax);
            } else {
                int available = 0;
                for (WarehouseStorageBlockEntity tile : destinations) {
                    available += tile.getQuantityStored(filter);
                }
                if (toMoveMax > available) {
                    toMoveMax = available;
                }
                toMoveMax = (int) Math.ceil(toMoveMax / 2.0);
            }
        }
        int toRemove = toMoveMax - stackSize;
        ItemStack removedStack = tryGetItem(filter, toRemove);

        ItemStack newCursorStack = filter.copy();
        newCursorStack.setCount(stackSize + removedStack.getCount());
        InventoryTools.updateCursorItem(player, newCursorStack, !rightClick && shiftClick);
    }

    private ItemStack tryGetItem(ItemStack filter, int toRemove) {
        if (level != null && level.isClientSide) {
            return tryGetItemClient(filter, toRemove);
        }

        int removed = 0;
        for (WarehouseStorageBlockEntity tile : storageMap.getDestinations()) {
            int count = tile.getQuantityStored(filter);
            int removeFromTile = Math.min(toRemove - removed, count);
            if (removeFromTile > 0) {
                removed += removeFromTile;
                tile.extractItem(filter, removeFromTile);
                changeCachedQuantity(filter, -removeFromTile);
                updateViewers();
            }
            if (removed >= toRemove) {
                break;
            }
        }

        if (removed == 0) {
            return ItemStack.EMPTY;
        }
        return filter.copyWithCount(removed);
    }

    private ItemStack tryGetItemClient(ItemStack filter, int toRemove) {
        int maxRemove = cachedItemMap.getCount(filter);
        int removed = Math.min(toRemove, maxRemove);
        cachedItemMap.decreaseCount(filter, removed);
        return filter.copyWithCount(removed);
    }

    // ************************************** INTERFACE WORK ***************************************//

    private static final IWorksiteAction EMPTY_ACTION = e -> IWorkSite.WorksiteImplementation.getEnergyPerActivation(e) / 4D;
    private static final IWorksiteAction FILL_ACTION = e -> IWorkSite.WorksiteImplementation.getEnergyPerActivation(e) / 4D;

    @Override
    protected Optional<IWorksiteAction> getNextAction() {
        if (!interfacesToEmpty.isEmpty()) {
            return Optional.of(EMPTY_ACTION);
        } else if (!interfacesToFill.isEmpty()) {
            return Optional.of(FILL_ACTION);
        }
        return Optional.empty();
    }

    @Override
    protected boolean processAction(IWorksiteAction action) {
        if (action == EMPTY_ACTION) {
            return tryEmptyInterfaces();
        } else if (action == FILL_ACTION) {
            return tryFillInterfaces();
        }
        return false;
    }

    private boolean tryEmptyInterfaces() {
        List<WarehouseInterfaceBlockEntity> toEmpty = new ArrayList<>(interfacesToEmpty);
        for (WarehouseInterfaceBlockEntity tile : toEmpty) {
            if (tryEmptyTile(tile)) {
                tile.recalcRequests();
                return true;
            }
        }
        return false;
    }

    private boolean tryEmptyTile(WarehouseInterfaceBlockEntity tile) {
        List<InterfaceEmptyRequest> reqs = tile.getEmptyRequests();
        for (InterfaceEmptyRequest req : reqs) {
            if (tryRemoveFromRequest(tile, req)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryRemoveFromRequest(WarehouseInterfaceBlockEntity tile, InterfaceEmptyRequest request) {
        IItemHandler inventory = tile.getInventory();
        ItemStack stack = inventory.getStackInSlot(request.slotNum());
        if (stack.isEmpty()) {
            return false;
        }
        int moved;
        int toMove = request.count();
        int stackMove;
        List<WarehouseStorageBlockEntity> potentialStorage = storageMap.getDestinations(stack);
        for (WarehouseStorageBlockEntity dest : potentialStorage) {
            stackMove = Math.min(toMove, stack.getCount());
            moved = dest.insertItem(stack, stackMove);
            if (moved > 0) {
                changeCachedQuantity(stack.copyWithCount(1), moved);
            }

            toMove -= moved;
            if (!inventory.extractItem(request.slotNum(), moved, false).isEmpty()) {
                return true;
            }
            if (toMove <= 0) {
                break;
            }
        }
        return false;
    }

    private boolean tryFillInterfaces() {
        List<WarehouseInterfaceBlockEntity> toFill = new ArrayList<>(interfacesToFill);
        for (WarehouseInterfaceBlockEntity tile : toFill) {
            if (tryFillTile(tile)) {
                tile.recalcRequests();
                return true;
            }
        }
        return false;
    }

    private boolean tryFillTile(WarehouseInterfaceBlockEntity tile) {
        List<InterfaceFillRequest> reqs = tile.getFillRequests();
        for (InterfaceFillRequest req : reqs) {
            if (tryFillFromRequest(tile, req)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryFillFromRequest(WarehouseInterfaceBlockEntity tile, InterfaceFillRequest request) {
        List<WarehouseStorageBlockEntity> potentialStorage = storageMap.getDestinations();
        IItemHandler inventory = tile.getInventory();
        int remainingToTake = request.requestAmount();
        for (WarehouseStorageBlockEntity source : potentialStorage) {
            int found = source.getQuantityStored(request.requestedItem());
            if (found > 0) {
                ItemStack stack = request.requestedItem().copy();
                int countToTake = Math.min(Math.min(stack.getMaxStackSize(), found), remainingToTake);

                stack.setCount(countToTake);
                stack = InventoryTools.mergeItemStack(inventory, stack);
                if (stack.isEmpty() || stack.getCount() != countToTake) {
                    int moved = stack.isEmpty() ? countToTake : countToTake - stack.getCount();
                    source.extractItem(request.requestedItem(), moved);
                    cachedItemMap.decreaseCount(request.requestedItem(), moved);
                    updateViewers();
                    remainingToTake -= moved;
                    if (remainingToTake <= 0) {
                        return true;
                    }
                }
            }
        }
        return remainingToTake != request.requestAmount();
    }

    // ************************************** CRAFTING STATION ***************************************//

    /**
     * A virtual item handler over the warehouse for the crafting station: the cached items as stacks of at most their
     * max stack size, plus ten empty slots to put results into. Inserting and extracting calls back into the real
     * storage. A new handler is built on every call (the slot contents are a snapshot of the cache).
     */
    public IItemHandlerModifiable getItemHandler() {
        int additionalSlots = 10;
        NonNullList<ItemStack> cachedItems = cachedItemMap.getItems();

        for (int i = 0; i < additionalSlots; i++) {
            cachedItems.add(ItemStack.EMPTY);
        }

        return new WarehouseItemHandler(cachedItems);
    }

    private class WarehouseItemHandler implements IItemHandlerModifiable {
        private final NonNullList<ItemStack> cachedItems;

        private WarehouseItemHandler(NonNullList<ItemStack> cachedItems) {
            this.cachedItems = cachedItems;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            ItemStack currentStack = cachedItems.get(slot);

            if (currentStack.isEmpty()) {
                insertItem(slot, stack, false);
            } else {
                int countChange = stack.getCount() - currentStack.getCount();

                if (countChange == 0) {
                    return;
                }

                if (countChange > 0) {
                    ItemStack copy = stack.copyWithCount(countChange);
                    insertItem(slot, copy, false);
                } else {
                    extractItem(slot, -countChange, false);
                }
            }
        }

        @Override
        public int getSlots() {
            return cachedItems.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot < cachedItems.size() ? cachedItems.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            ItemStack cachedStack = cachedItems.get(slot);
            if (cachedStack.isEmpty() || InventoryTools.doItemStacksMatchRelaxed(stack, cachedStack)) {
                int maxToAdd = Math.min(stack.getMaxStackSize() - cachedStack.getCount(), stack.getCount());
                if (!simulate) {
                    if (!cachedStack.isEmpty()) { // the shared ItemStack.EMPTY must never be changed (1.12.2 did)
                        cachedStack.grow(maxToAdd);
                    }
                    ItemStack result = tryAddItem(stack, maxToAdd);
                    if (maxToAdd == stack.getCount()) {
                        return result;
                    } else {
                        return stack.copyWithCount(stack.getCount() - maxToAdd + result.getCount());
                    }
                }
                ItemStack ret = ItemStack.EMPTY;
                if (maxToAdd < stack.getCount()) {
                    ret = stack.copyWithCount(stack.getCount() - maxToAdd);
                }

                return ret;
            }

            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack cachedStack = cachedItems.get(slot);

            if (cachedStack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            int maxToRemove = Math.min(cachedStack.getCount(), amount);

            if (!simulate) {
                ItemStack result = tryGetItem(cachedStack, maxToRemove);
                cachedStack.setCount(cachedStack.getCount() - maxToRemove);
                return result;
            }

            return cachedStack.copyWithCount(maxToRemove);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            ItemStack cachedStack = cachedItems.get(slot);
            return cachedStack.isEmpty() || InventoryTools.doItemStacksMatchRelaxed(stack, cachedStack);
        }
    }

    // ************************************** MISC ***************************************//

    @Override
    public final IWorkSite.WorkType getWorkType() {
        return IWorkSite.WorkType.CRAFTING;
    }

    @Override
    public final boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide) {
            WarehouseMenus.openControl(player, this);
        }
        return true;
    }

    public SortType getSortType() {
        return sortType;
    }

    public void setSortType(SortType sortType) {
        this.sortType = sortType;
        setChanged();
    }

    public SortOrder getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
        setChanged();
    }

    // ************************************** NBT AND PACKET DATA METHODS ***************************************//

    private void writeSort(CompoundTag tag) {
        tag.putByte("sortOrder", (byte) sortOrder.ordinal());
        tag.putByte("sortType", (byte) sortType.ordinal());
    }

    private void readSort(CompoundTag tag) {
        SortOrder[] orders = SortOrder.values();
        SortType[] types = SortType.values();
        int order = tag.getByte("sortOrder");
        int type = tag.getByte("sortType");
        sortOrder = order >= 0 && order < orders.length ? orders[order] : SortOrder.DESCENDING;
        sortType = type >= 0 && type < types.length ? types[type] : SortType.NAME;
    }

    @Override
    protected void writeUpdateNBT(CompoundTag tag) {
        super.writeUpdateNBT(tag);
        writeSort(tag);
    }

    @Override
    protected void handleUpdateNBT(CompoundTag tag) {
        super.handleUpdateNBT(tag);
        readSort(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag linkers = new ListTag();
        for (BlockPos linkerPos : stockLinkerPositions) {
            linkers.add(LongTag.valueOf(linkerPos.asLong()));
        }
        tag.put(STOCK_LINKER_POSITIONS_TAG, linkers);
        writeSort(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stockLinkerPositions = new HashSet<>();
        ListTag linkers = tag.getList(STOCK_LINKER_POSITIONS_TAG, Tag.TAG_LONG);
        for (int i = 0; i < linkers.size(); i++) {
            if (linkers.get(i) instanceof LongTag longTag) {
                stockLinkerPositions.add(BlockPos.of(longTag.getAsLong()));
            }
        }
        readSort(tag);
    }
}
