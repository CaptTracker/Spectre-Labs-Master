/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/warehouse2/TileWarehouseStockLinker.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.warehouse;

import io.github.capttracker.ancientwarfare.api.IInteractableTile;
import io.github.capttracker.ancientwarfare.owner.IOwnable;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfare.util.NBTHelper;
import io.github.capttracker.ancientwarfareautomation.block.warehouse.WarehouseStockLinkerBlock;
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
 * The warehouse stock linker: a redstone output driven by the item counts of one warehouse. It has up to four
 * filters, each an item, a comparison ({@code =, >, <, >=, <=}) and a value; the block gives a redstone signal (15)
 * while any filter's comparison is true. A compare value of 0 is never true (kept from the original).
 *
 * <p>Unlike the other controlled blocks it is not tied to a position in the warehouse bounds: it is linked to a
 * specific control block, the one whose position was stored in the item stack it was placed from
 * ({@code WarehouseStockLinkerBlockItem}, set on the block entity with {@link #setWarehousePos}), and accepts any
 * controller. The linking is retried every 40 ticks until it works. The {@code lit} block state follows the
 * comparison result, re-checked at most every 60 ticks.
 *
 * <p>Changes from the original: the warehouse position is saved (the original lost it on restart and relied on the
 * saved controller position alone), all ticking is server side (the original also changed the block state on the
 * client), and detaching from the controller no longer wipes the comparison settings of the filters (it reset sign
 * and value to the defaults).
 */
public class WarehouseStockLinkerBlockEntity extends ControlledBlockEntity implements IOwnable, IInteractableTile {

    private static final String FILTER_LIST_TAG = "filterList";
    private static final String WAREHOUSE_POS_TAG = "warehousePos";

    /** An open stock linker screen: told when the filter list changed (client side, from the update tag). */
    public interface Viewer {
        void onFiltersChanged();
    }

    private final List<WarehouseStockFilter> filters = new ArrayList<>();
    private Owner owner = Owner.EMPTY;
    @Nullable
    private BlockPos warehouseBlockPos;

    private final Set<Viewer> viewers = new HashSet<>();
    private int searchCooldown = 0;
    private int blockUpdateCooldown = 0;
    private boolean currentEquality;

    public WarehouseStockLinkerBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.WAREHOUSE_STOCK_LINKER_BE.get(), pos, state);
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

    /** The control block to link to (from the item the linker was placed from). */
    public void setWarehousePos(@Nullable BlockPos pos) {
        this.warehouseBlockPos = pos;
        setChanged();
    }

    public List<WarehouseStockFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<WarehouseStockFilter> filters) {
        this.filters.clear();
        this.filters.addAll(filters);
        setChanged();
        BlockTools.notifyBlockUpdate(this);
        recountFilters();
        BlockTools.notifyNeighbors(this);
    }

    /** Whether any filter's comparison is true: the redstone output of the block. */
    public boolean getEqualityHandle() {
        if (!filters.isEmpty()) {
            for (WarehouseStockFilter stockFilter : filters) {
                if (handleEqualitySign(stockFilter, stockFilter.getCompareValue(), stockFilter.getQuantity())) {
                    return true;
                }
            }
        }
        return false;
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
        } else if (controller.get().isActive()) {
            for (WarehouseStockFilter filter : this.filters) {
                filter.setQuantity(filter.getFilterItem().isEmpty() ? 0 : controller.get().getCountOf(filter.getFilterItem()));
            }
        }
    }

    @Override
    public void searchForController() {
        linkToWarehouse();
    }

    private void linkToWarehouse() {
        if (level != null && warehouseBlockPos != null && level.isLoaded(warehouseBlockPos)) {
            if (level.getBlockEntity(warehouseBlockPos) instanceof WarehouseBlockEntity warehouse && isValidController(warehouse)) {
                warehouse.addControlledTile(this);
            }
        }
    }

    /** Any control block is accepted; the link is made through {@link #setWarehousePos}. */
    @Override
    public boolean isValidController(IControllerTile tile) {
        return tile instanceof WarehouseBlockEntity;
    }

    @Override
    protected void onControllerChanged() {
        onWarehouseInventoryUpdated();
    }

    @Override
    protected void updateTile() {
        if (level == null) {
            return;
        }
        if (searchCooldown > 0) {
            searchCooldown--;
        }
        if (getController().isEmpty() && canSearchForWarehouseAgain()) {
            linkToWarehouse();
            searchCooldown = 40;
        }
        if (blockUpdateCooldown > 0) {
            blockUpdateCooldown--;
        }
        if (canDoBlockUpdateAgain()) {
            boolean equality = getEqualityHandle();
            WarehouseStockLinkerBlock.setActiveState(equality, level, worldPosition);
            blockUpdateCooldown = 60;
            currentEquality = equality;
        }
    }

    private boolean canSearchForWarehouseAgain() {
        return searchCooldown <= 0;
    }

    private boolean canDoBlockUpdateAgain() {
        return blockUpdateCooldown <= 0 && currentEquality != getEqualityHandle();
    }

    /*
     * should be called on SERVER whenever warehouse inventory changes
     */
    void onWarehouseInventoryUpdated() {
        if (level != null && !level.isClientSide) {
            recountFilters();
            BlockTools.notifyNeighbors(this);
            BlockTools.notifyBlockUpdate(this);
        }
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
            WarehouseMenus.openStockLinker(player, this);
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
        filters.clear();
        filters.addAll(NBTHelper.deserializeListFrom(registries, tag, FILTER_LIST_TAG, WarehouseStockFilter::new));
        BlockTools.notifyBlockUpdate(this);
        updateViewers();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        filters.clear();
        filters.addAll(NBTHelper.deserializeListFrom(registries, tag, FILTER_LIST_TAG, WarehouseStockFilter::new));
        owner = Owner.deserializeFromNBT(tag);
        warehouseBlockPos = tag.contains(WAREHOUSE_POS_TAG) ? BlockPos.of(tag.getLong(WAREHOUSE_POS_TAG)) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        NBTHelper.writeSerializablesTo(registries, tag, FILTER_LIST_TAG, filters);
        owner.serializeToNBT(tag);
        if (warehouseBlockPos != null) {
            tag.putLong(WAREHOUSE_POS_TAG, warehouseBlockPos.asLong());
        }
    }

    // ************************************** FILTERS ***************************************//

    /** One comparison: an item, how its count in the warehouse is compared and the value it is compared to. */
    public static class WarehouseStockFilter implements INBTSerializable<CompoundTag> {
        private static final String ITEM_TAG = "item";
        private static final String QUANTITY_TAG = "quantity";
        private static final String EQUALITY_TAG = "equality_sign";
        private static final String COMPARE_VALUE_TAG = "compare_value";
        private ItemStack item = ItemStack.EMPTY;
        public EqualitySignType equalitySignType = EqualitySignType.EQUAL_TO;
        public int compareValue;
        public int quantity;

        public WarehouseStockFilter() {}

        public WarehouseStockFilter(ItemStack item, int equalitySign, int qty, int compareValue) {
            setItem(item);
            setEqualitySignType((byte) equalitySign);
            setQuantity(qty);
            setCompareValue(compareValue);
        }

        public void setItem(ItemStack item) {
            this.item = item;
        }

        public void setEqualitySignType(byte equalitySignType) {
            EqualitySignType[] values = EqualitySignType.values();
            this.equalitySignType = equalitySignType >= 0 && equalitySignType < values.length ? values[equalitySignType] : EqualitySignType.EQUAL_TO;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public void setCompareValue(int compareValue) {
            this.compareValue = compareValue;
        }

        public ItemStack getFilterItem() {
            return item;
        }

        public EqualitySignType getEqualitySignType() {
            return equalitySignType;
        }

        public int getCompareValue() {
            return compareValue;
        }

        public int getQuantity() {
            return quantity;
        }

        /** Cycles the comparison: forward, or backward for a right click. */
        public void changeEqualitySign(boolean isRmb) {
            equalitySignType = isRmb ? equalitySignType.previous() : equalitySignType.next();
        }

        private boolean equalToTarget(int compareValue, int quantity) {
            return compareValue != 0 && quantity == compareValue;
        }

        private boolean greaterThanTarget(int compareValue, int quantity) {
            return compareValue != 0 && quantity > compareValue;
        }

        private boolean lessThanTarget(int compareValue, int quantity) {
            return compareValue != 0 && quantity < compareValue;
        }

        private boolean greaterThanOrEqualToTarget(int compareValue, int quantity) {
            return compareValue != 0 && quantity >= compareValue;
        }

        private boolean lessThanOrEqualToTarget(int compareValue, int quantity) {
            return compareValue != 0 && quantity <= compareValue;
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            if (!item.isEmpty()) {
                tag.put(ITEM_TAG, item.saveOptional(registries));
            }
            tag.putByte(EQUALITY_TAG, (byte) equalitySignType.ordinal());
            tag.putInt(QUANTITY_TAG, quantity);
            tag.putInt(COMPARE_VALUE_TAG, compareValue);
            return tag;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
            setItem(tag.contains(ITEM_TAG) ? ItemStack.parseOptional(registries, tag.getCompound(ITEM_TAG)) : ItemStack.EMPTY);
            setEqualitySignType(tag.getByte(EQUALITY_TAG));
            setQuantity(tag.getInt(QUANTITY_TAG));
            setCompareValue(tag.getInt(COMPARE_VALUE_TAG));
        }
    }

    /** The comparisons a filter offers, in the order the screen cycles through them. */
    public enum EqualitySignType {
        EQUAL_TO("equality.equal"),

        GREATER_THAN("equality.greaterthan"),

        LESS_THAN("equality.lessthan"),

        GREATER_THAN_OR_EQUAL_TO("equality.greaterthanorequalto"),

        LESS_THAN_OR_EQUAL_TO("equality.lessthanorequalto");

        final String key;

        EqualitySignType(String key) {
            this.key = key;
        }

        /** The lang key of the sign ({@code =, >, <, >=, <=}). */
        public String getTranslationKey() {
            return key;
        }

        public EqualitySignType next() {
            int ordinal = ordinal() + 1;
            if (ordinal >= EqualitySignType.values().length) {
                ordinal = 0;
            }
            return EqualitySignType.values()[ordinal];
        }

        public EqualitySignType previous() {
            int ordinal = ordinal() - 1;
            if (ordinal < 0) {
                ordinal = EqualitySignType.values().length - 1;
            }
            return EqualitySignType.values()[ordinal];
        }
    }

    private boolean handleEqualitySign(WarehouseStockFilter w, int compareValue, int quantity) {
        return switch (w.equalitySignType) {
            case EQUAL_TO -> w.equalToTarget(compareValue, quantity);
            case GREATER_THAN -> w.greaterThanTarget(compareValue, quantity);
            case LESS_THAN -> w.lessThanTarget(compareValue, quantity);
            case GREATER_THAN_OR_EQUAL_TO -> w.greaterThanOrEqualToTarget(compareValue, quantity);
            case LESS_THAN_OR_EQUAL_TO -> w.lessThanOrEqualToTarget(compareValue, quantity);
        };
    }
}
