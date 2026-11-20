/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/warehouse2/TileWarehouseStorage, TileWarehouseStorageMedium,
 * TileWarehouseStorageLarge and IWarehouseStorageTile.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.warehouse;

import io.github.capttracker.ancientwarfare.api.IBlockBreakHandler;
import io.github.capttracker.ancientwarfare.api.IInteractableTile;
import io.github.capttracker.ancientwarfare.inventory.InventorySlotlessBasic;
import io.github.capttracker.ancientwarfare.inventory.ItemQuantityMap;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfare.util.NBTHelper;
import io.github.capttracker.ancientwarfareautomation.block.warehouse.WarehouseStorageBlock;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import io.github.capttracker.ancientwarfareautomation.init.WarehouseSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A warehouse storage block: a slotless store of item counts with a total capacity of 576 items (small), 1,152
 * (medium) or 1,728 (large). One block entity type serves the three blocks; the size is read from the block
 * ({@link WarehouseStorageBlock#getSize()}). Replaces the 1.12.2 classes {@code TileWarehouseStorage},
 * {@code TileWarehouseStorageMedium}, {@code TileWarehouseStorageLarge} and the interface {@code IWarehouseStorageTile}
 * (this was its only implementer).
 *
 * <p>Items are counted per {@code ItemHashEntry} (item plus data components); the capacity counts items, not stacks.
 * The filter list restricts what the warehouse routes into this block (it does not stop a player putting something
 * else in by hand in the storage screen, nor does it move items already stored). The menu of this block (the GUI slice)
 * registers as a {@link Viewer} to be told about changes.
 *
 * <p>Saved: {@code inventory} (the slotless store), {@code filterList}, and the controller position. Nothing is sent to
 * clients in the update tag; open menus get changes through the {@link Viewer} callbacks.
 */
public class WarehouseStorageBlockEntity extends ControlledBlockEntity implements IBlockBreakHandler, IInteractableTile {
    /** The capacity of a small storage block; medium is twice and large three times this. */
    public static final int BASE_STORAGE_SIZE = 9 * 64;

    /** An open storage menu: told when the stored items or the filter list changed (server side). */
    public interface Viewer {
        void onStorageInventoryUpdated();

        void onFilterListUpdated();
    }

    private final WarehouseSize size;
    private final InventorySlotlessBasic inventory;
    private final List<WarehouseStorageFilter> filters = new ArrayList<>();

    private final Set<Viewer> viewers = new HashSet<>();

    public WarehouseStorageBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.WAREHOUSE_STORAGE_BE.get(), pos, state);
        this.size = state.getBlock() instanceof WarehouseStorageBlock block ? block.getSize() : WarehouseSize.SMALL;
        this.inventory = new InventorySlotlessBasic(getStorageAdditionSize());
    }

    public WarehouseSize getSize() {
        return size;
    }

    /** The capacity of this block in items: 576, 1,152 or 1,728. */
    public final int getStorageAdditionSize() {
        return BASE_STORAGE_SIZE * (size.ordinal() + 1);
    }

    @Override
    protected void updateTile() {
        //noop
    }

    /**
     * Puts as much of the stack as fits into this block (ignoring the filters) and tells the controller. Consumes what
     * it takes from the stack.
     *
     * @return what did not fit ({@link ItemStack#EMPTY} if everything did)
     */
    public ItemStack tryAdd(ItemStack cursorStack) {
        int moved = insertItem(cursorStack, cursorStack.getCount());
        getController().ifPresent(controller -> controller.changeCachedQuantity(cursorStack.copyWithCount(1), moved));
        cursorStack.shrink(moved);
        if (cursorStack.getCount() <= 0) {
            return ItemStack.EMPTY;
        }
        return cursorStack;
    }

    @Override
    public void onBlockBroken(BlockState state) {
        if (level == null) {
            return;
        }
        ItemQuantityMap qtm = new ItemQuantityMap();
        addItems(qtm);
        NonNullList<ItemStack> list = qtm.getItems();
        for (ItemStack stack : list) {
            InventoryTools.dropItemInWorld(level, stack, worldPosition);
        }
    }

    // ************************************** STORAGE ***************************************//

    public List<WarehouseStorageFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<WarehouseStorageFilter> filters) {
        List<WarehouseStorageFilter> old = new ArrayList<>(this.filters);
        this.filters.clear();
        this.filters.addAll(filters);
        getController().ifPresent(controller -> controller.onStorageFilterChanged(this, old, this.filters));
        updateViewers();
        setChanged();
    }

    /** Adds all items of this block to the map. */
    public void addItems(ItemQuantityMap map) {
        inventory.getItems(map);
    }

    public int getQuantityStored(ItemStack filter) {
        return inventory.getQuantityStored(filter);
    }

    public int getAvailableSpaceFor(ItemStack filter) {
        return inventory.getAvailableSpaceFor(filter);
    }

    /** @return the amount that was taken out */
    public int extractItem(ItemStack filter, int amount) {
        int removed = inventory.extractItem(filter, amount);
        updateViewersForInventory();
        if (removed > 0) {
            setChanged();
        }
        return removed;
    }

    /** @return the amount that was put in (limited by the free capacity) */
    public int insertItem(ItemStack filter, int amount) {
        int inserted = inventory.insertItem(filter, amount);
        updateViewersForInventory();
        if (inserted > 0) {
            setChanged();
        }
        return inserted;
    }

    // ************************************** VIEWERS ***************************************//

    public void addViewer(Viewer viewer) {
        if (level == null || level.isClientSide) {
            return;
        }
        viewers.add(viewer);
    }

    public void removeViewer(Viewer viewer) {
        viewers.remove(viewer);
    }

    private void updateViewers() {
        for (Viewer viewer : viewers) {
            viewer.onFilterListUpdated();
        }
    }

    private void updateViewersForInventory() {
        for (Viewer viewer : viewers) {
            viewer.onStorageInventoryUpdated();
        }
    }

    // ************************************** INTERACTION ***************************************//

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide) {
            WarehouseMenus.openStorage(player, this);
        }
        return true;
    }

    /**
     * A click on an item in the storage screen (server side; the menu calls it after validating the request).
     * With an item on the cursor (and no shift) the cursor stack is put into this block, otherwise the clicked item is
     * taken out: left click one stack, right click half a stack, shift-right click one more than the cursor holds,
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
        } else {
            tryGetItem(serverPlayer, filter, shiftClick, rightClick);
        }
    }

    private void tryAddItem(ServerPlayer player, ItemStack cursorStack) {
        int stackSize = cursorStack.getCount();
        int moved = insertItem(cursorStack, cursorStack.getCount());
        getController().ifPresent(controller -> controller.changeCachedQuantity(cursorStack.copyWithCount(1), moved));
        cursorStack.shrink(moved);
        if (cursorStack.getCount() <= 0) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        if (stackSize != cursorStack.getCount()) {
            player.containerMenu.broadcastChanges();
        }
    }

    private void tryGetItem(ServerPlayer player, ItemStack filter, boolean shiftClick, boolean rightClick) {
        int stackSize = 0;
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            stackSize = carried.getCount();
            ItemStack comparableStack = carried.copyWithCount(filter.getCount());
            if (!ItemStack.matches(filter, comparableStack)) {
                return;
            }
        }

        int count = getQuantityStored(filter);
        int toMoveMax = filter.getMaxStackSize();
        if (rightClick && (toMoveMax > 1)) {
            if (shiftClick) {
                toMoveMax = Math.min(stackSize + 1, toMoveMax);
            } else {
                if (toMoveMax > count) {
                    toMoveMax = count;
                }
                toMoveMax = (int) Math.ceil(toMoveMax / 2.0);
            }
        }
        int toMove = toMoveMax - stackSize;
        toMove = Math.min(toMove, count);
        if (toMove > 0) {
            extractItem(filter, toMove);
            int cacheChange = toMove; //because we need final variable for lambda
            getController().ifPresent(controller -> controller.changeCachedQuantity(filter, -cacheChange));
        }
        ItemStack newCursorStack = filter.copy();
        newCursorStack.setCount(stackSize + toMove);
        InventoryTools.updateCursorItem(player, newCursorStack, !rightClick && shiftClick);
    }

    // ************************************** NBT ***************************************//

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.readFromNBT(registries, tag.getCompound("inventory"));
        filters.clear();
        filters.addAll(NBTHelper.deserializeListFrom(registries, tag, "filterList", WarehouseStorageFilter::new));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.writeToNBT(registries, new CompoundTag()));
        NBTHelper.writeSerializablesTo(registries, tag, "filterList", filters);
    }
}
