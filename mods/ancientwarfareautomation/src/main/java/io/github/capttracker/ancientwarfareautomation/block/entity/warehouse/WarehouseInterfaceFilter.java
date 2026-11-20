/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/warehouse2/WarehouseInterfaceFilter.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.warehouse;

import io.github.capttracker.ancientwarfare.inventory.ItemHashEntry;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * An interface block filter: an item and the quantity to keep in the interface. It matches stacks with Core's relaxed
 * comparison (same item and components, damage ignored); 1.12.2 also accepted ore dictionary equivalents, which are
 * not available (see the notes of the slice).
 */
public final class WarehouseInterfaceFilter implements Predicate<ItemStack>, INBTSerializable<CompoundTag> {
    private static final String FILTER_TAG = "filter";

    private ItemStack filterItem = ItemStack.EMPTY;
    private int quantity;

    /** @return whether the stack matches the filter item (the 1.12.2 {@code apply}) */
    @Override
    public boolean test(ItemStack item) {
        return InventoryTools.doItemStacksMatchRelaxed(filterItem, item);
    }

    @Override
    public boolean equals(@Nullable Object object) {
        return object instanceof WarehouseInterfaceFilter other && other.quantity == this.quantity && ItemStack.matches(this.filterItem, other.filterItem);
    }

    @Override
    public int hashCode() {
        int result = !getFilterItem().isEmpty() ? new ItemHashEntry(getFilterItem()).hashCode() : 0;
        return 31 * result + quantity;
    }

    public ItemStack getFilterItem() {
        return filterItem;
    }

    public void setFilterItem(ItemStack item) {
        this.filterItem = item;
    }

    public int getFilterQuantity() {
        return quantity;
    }

    public void setFilterQuantity(int filterQuantity) {
        this.quantity = filterQuantity;
    }

    @Override
    public String toString() {
        return "Filter item: " + filterItem + " quantity: " + quantity;
    }

    public WarehouseInterfaceFilter copy() {
        WarehouseInterfaceFilter filter = new WarehouseInterfaceFilter();
        if (!this.filterItem.isEmpty()) {
            filter.setFilterItem(this.filterItem.copy());
        }
        filter.setFilterQuantity(this.quantity);
        return filter;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("quantity", quantity);
        if (!filterItem.isEmpty()) {
            tag.put(FILTER_TAG, filterItem.saveOptional(registries));
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        quantity = tag.getInt("quantity");
        if (tag.contains(FILTER_TAG)) {
            filterItem = ItemStack.parseOptional(registries, tag.getCompound(FILTER_TAG));
        }
    }
}
