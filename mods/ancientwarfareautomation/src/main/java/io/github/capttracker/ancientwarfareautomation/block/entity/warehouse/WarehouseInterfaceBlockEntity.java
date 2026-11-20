/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/warehouse2/TileWarehouseInterface.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.warehouse;

import io.github.capttracker.ancientwarfare.api.IBlockBreakHandler;
import io.github.capttracker.ancientwarfare.api.IInteractableTile;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfare.util.NBTHelper;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The warehouse interface block: the bridge between a warehouse and the rest of the automation. It holds nine item
 * slots (an item handler on every side, see {@code WarehouseSetup}) and a list of filters. For each filter the
 * warehouse keeps the interface filled up to the filter's quantity; items that match no filter, or exceed the
 * summed quantity of the filters they match, are moved back into the warehouse. The control block does the moving as
 * worksite actions, one stack per action ({@link WarehouseBlockEntity}).
 *
 * <p>{@link #recalcRequests()} runs on every change of the nine slots and of the filters and tells the controller which
 * requests there are. The menu of the GUI slice registers as a {@link Viewer}.
 *
 * <p>Saved: {@code inventory}, {@code filterList}, the controller position.
 */
public class WarehouseInterfaceBlockEntity extends ControlledBlockEntity implements IInteractableTile, IBlockBreakHandler {

    /** An open interface menu: told when the filter list changed (server side). */
    public interface Viewer {
        void onInterfaceFiltersChanged();
    }

    /** A request to bring an item up to a quantity: the item (count 1 or the filter's stack) and how many are missing. */
    public record InterfaceFillRequest(ItemStack requestedItem, int requestAmount) {
    }

    /** A request to move items out of a slot back into the warehouse. */
    public record InterfaceEmptyRequest(int slotNum, int count) {
    }

    private final ItemStackHandler inventory = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            recalcRequests();
        }
    };

    private boolean init = false;
    private final List<InterfaceFillRequest> fillRequests = new ArrayList<>();
    private final List<InterfaceEmptyRequest> emptyRequests = new ArrayList<>();
    private List<WarehouseInterfaceFilter> filters = new ArrayList<>();
    private final List<Viewer> viewers = new ArrayList<>();

    public WarehouseInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.WAREHOUSE_INTERFACE_BE.get(), pos, state);
    }

    /** The nine slots: what the item handler capability shows on every side. */
    public ItemStackHandler getInventory() {
        return inventory;
    }

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
        for (Viewer v : viewers) {
            v.onInterfaceFiltersChanged();
        }
    }

    public List<WarehouseInterfaceFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<WarehouseInterfaceFilter> filters) {
        this.filters.clear();
        this.filters.addAll(filters);
        recalcRequests();
        updateViewers();
        setChanged();
    }

    @Override
    protected void updateTile() {
        if (!init) {
            init = true;
            recalcRequests();
        }
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide) {
            WarehouseMenus.openInterface(player, this);
        }
        return true;
    }

    // ************************************** REQUESTS ***************************************//

    /** Works out what has to be filled and what has to be emptied and tells the controller. Server only. */
    public void recalcRequests() {
        if (level == null || level.isClientSide) {
            return;
        }
        fillRequests.clear();
        emptyRequests.clear();
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!matchesFilter(stack)) {
                emptyRequests.add(new InterfaceEmptyRequest(i, stack.getCount()));
            } else//matches, remove extras
            {
                int count = InventoryTools.getCountOf(inventory, stack);
                int max = getFilterQuantity(stack);
                if (count > max) {
                    emptyRequests.add(new InterfaceEmptyRequest(i, count - max));
                }
            }
        }

        for (WarehouseInterfaceFilter filter : filters) {
            if (filter.getFilterItem().isEmpty()) {
                continue;
            }
            int count = InventoryTools.getCountOf(inventory, filter.getFilterItem());
            if (count < filter.getFilterQuantity()) {
                fillRequests.add(new InterfaceFillRequest(filter.getFilterItem().copy(), filter.getFilterQuantity() - count));
            }
        }
        getController().ifPresent(controller -> controller.onIterfaceInventoryChanged(this));
    }

    private boolean matchesFilter(ItemStack stack) {
        if (filters.isEmpty()) {
            return false;
        }
        for (WarehouseInterfaceFilter filter : filters) {
            if (filter.test(stack)) {
                return true;
            }
        }
        return false;
    }

    private int getFilterQuantity(ItemStack stack) {
        int qty = 0;
        for (WarehouseInterfaceFilter filter : filters) {
            if (filter.test(stack)) {
                qty += filter.getFilterQuantity();
            }
        }
        return qty;
    }

    public List<InterfaceFillRequest> getFillRequests() {
        return fillRequests;
    }

    public List<InterfaceEmptyRequest> getEmptyRequests() {
        return emptyRequests;
    }

    @Override
    public void onBlockBroken(BlockState state) {
        if (level != null) {
            InventoryTools.dropItemsInWorld(level, inventory, worldPosition);
        }
    }

    // ************************************** NBT ***************************************//

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        filters = NBTHelper.deserializeListFrom(registries, tag, "filterList", WarehouseInterfaceFilter::new);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        NBTHelper.writeSerializablesTo(registries, tag, "filterList", getFilters());
    }
}
