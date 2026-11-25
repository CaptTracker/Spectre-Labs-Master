/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/warehouse2/TileWarehouseStockViewer.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.warehouse;

import io.github.capttracker.ancientwarfare.api.IInteractableTile;
import io.github.capttracker.ancientwarfare.owner.IOwnable;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfare.util.NBTHelper;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.INBTSerializable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The warehouse stock viewer: a sign on the outside of a warehouse that shows the live count of up to four items. A
 * filter is an item plus the quantity the warehouse holds of it ({@link WarehouseStockFilter}); the quantities are
 * recounted on the server whenever the warehouse content changes and sent to the clients in the update tag, where a
 * block entity renderer (client slice) draws them. The owner (and friends and team mates) can set the items in the
 * screen of the GUI slice ({@link Viewer}).
 *
 * <p>It belongs to the controller whose bounds, widened by one block horizontally, contain it, so it can sit in
 * the outer wall. Saved: filters (with quantity), owner, controller position.
 */
public class WarehouseStockViewerBlockEntity extends ControlledBlockEntity implements IOwnable, IInteractableTile {
    private static final String FILTER_LIST_TAG = "filterList";

    /** An open stock viewer screen: told when the filter list changed (client side, from the update tag). */
    public interface Viewer {
        void onFiltersChanged();
    }

    private final List<WarehouseStockFilter> filters = new ArrayList<>();
    private Owner owner = Owner.EMPTY;

    private final Set<Viewer> viewers = new HashSet<>();

    public WarehouseStockViewerBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.WAREHOUSE_STOCK_VIEWER_BE.get(), pos, state);
    }

    private void updateViewers() {
        for (Viewer viewer : viewers) {
            viewer.onFiltersChanged();
        }
    }

    public void addViewer(Viewer viewer) {
        viewers.add(viewer);
    }

    public void removeViewer(Viewer viewer) {
        viewers.remove(viewer);
    }

    public List<WarehouseStockFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<WarehouseStockFilter> filters) {
        this.filters.clear();
        this.filters.addAll(filters);
        recountFilters();//recount filters, do not send update
        setChanged();
        BlockTools.notifyBlockUpdate(this); //to re-send description packet to client with new filters
    }

    /*
     * should be called whenever controller tile is set or warehouse inventory updated
     */
    private void recountFilters() {
        Optional<WarehouseBlockEntity> controller = getController();
        if (controller.isEmpty()) {
            for (WarehouseStockFilter filter : this.filters) {
                filter.setQuantity(0);
            }
        } else {
            for (WarehouseStockFilter filter : this.filters) {
                filter.setQuantity(filter.getFilterItem().isEmpty() ? 0 : controller.get().getCountOf(filter.getFilterItem()));
            }
        }
    }

    /** The bounds of the controller widened by one block horizontally contain this block. */
    @Override
    public boolean isValidController(IControllerTile tile) {
        return BlockTools.isPositionWithinBounds(worldPosition, tile.getWorkBoundsMin().offset(-1, 0, -1), tile.getWorkBoundsMax().offset(1, 0, 1));
    }

    @Override
    protected void onControllerChanged() {
        onWarehouseInventoryUpdated();
    }

    @Override
    protected void updateTile() {
        //noop
    }

    /*
     * should be called on SERVER whenever warehouse inventory changes
     */
    void onWarehouseInventoryUpdated() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockTools.notifyBlockUpdate(this);
        recountFilters();
    }

    // ************************************** OWNER ***************************************//

    @Override
    public boolean isOwner(Player player) {
        return owner.isOwnerOrSameTeamOrFriend(player);
    }

    @Override
    public void setOwner(Player player) {
        setOwner(new Owner(player));
    }

    @Override
    public void setOwner(Owner owner) {
        this.owner = owner;
        setChanged();
    }

    @Override
    public Owner getOwner() {
        return owner;
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide && isOwner(player)) {
            WarehouseMenus.openStockViewer(player, this);
        }
        return true;
    }

    // ************************************** NBT AND PACKET DATA METHODS ***************************************//

    @Override
    protected void writeSyncData(CompoundTag tag, HolderLookup.Provider registries) {
        NBTHelper.writeSerializablesTo(registries, tag, FILTER_LIST_TAG, filters);
    }

    @Override
    protected void readSyncData(CompoundTag tag, HolderLookup.Provider registries) {
        this.filters.clear();
        this.filters.addAll(NBTHelper.deserializeListFrom(registries, tag, FILTER_LIST_TAG, WarehouseStockFilter::new));
        BlockTools.notifyBlockUpdate(this);
        updateViewers();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        filters.clear();
        filters.addAll(NBTHelper.deserializeListFrom(registries, tag, FILTER_LIST_TAG, WarehouseStockFilter::new));
        owner = Owner.deserializeFromNBT(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        NBTHelper.writeSerializablesTo(registries, tag, FILTER_LIST_TAG, filters);
        owner.serializeToNBT(tag);
    }

    /** One line of the sign: an item and the quantity of it the warehouse holds (kept up to date by the server). */
    public static class WarehouseStockFilter implements INBTSerializable<CompoundTag> {
        private static final String ITEM_TAG = "item";
        private static final String QUANTITY_TAG = "quantity";
        private ItemStack item = ItemStack.EMPTY;
        private int quantity;

        public WarehouseStockFilter() {}

        public WarehouseStockFilter(ItemStack item, int qty) {
            setQuantity(qty);
            setItem(item);
        }

        public void setItem(ItemStack item) {
            this.item = item;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public ItemStack getFilterItem() {
            return item;
        }

        public int getQuantity() {
            return quantity;
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            if (!item.isEmpty()) {
                tag.put(ITEM_TAG, item.saveOptional(registries));
            }
            tag.putInt(QUANTITY_TAG, quantity);
            return tag;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
            setItem(tag.contains(ITEM_TAG) ? ItemStack.parseOptional(registries, tag.getCompound(ITEM_TAG)) : ItemStack.EMPTY);
            setQuantity(tag.getInt(QUANTITY_TAG));
        }
    }
}
