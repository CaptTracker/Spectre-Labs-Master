/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/trade/POTradeTransferEntry.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

/**
 * One line of the restock lists of a trader: an item (the filter, its stack size is the quantity) and what to do with it
 * ({@link TransferType}). Stored as {@code item} (left out when empty) and {@code type} (the ordinal of the type).
 */
public abstract class POTradeTransferEntry {
    private TransferType type = getDefaultType();
    private ItemStack filter = ItemStack.EMPTY;

    protected abstract TransferType getDefaultType();

    public abstract void toggleType();

    protected abstract TransferType getTypeFrom(int type);

    public final ItemStack getFilter() {
        return filter;
    }

    public final void setFilter(ItemStack stack) {
        filter = stack;
    }

    public final void setType(@Nullable TransferType type) {
        this.type = type == null ? getDefaultType() : type;
    }

    public final TransferType getType() {
        return type;
    }

    public final void process(IItemHandler storage, IItemHandler move) {
        if (!filter.isEmpty()) {
            type.doTransfer(storage, move, filter);
        }
    }

    /** The filter for the codec. */
    final Optional<ItemStack> filterForCodec() {
        return filter.isEmpty() ? Optional.empty() : Optional.of(filter.copy());
    }

    /** Sets the data the codec read; an ordinal out of range reads as the default type (1.12.2 threw). */
    final void load(Optional<ItemStack> filter, int typeOrdinal) {
        this.filter = filter.map(ItemStack::copy).orElse(ItemStack.EMPTY);
        TransferType[] types = getTypes();
        this.type = typeOrdinal >= 0 && typeOrdinal < types.length ? getTypeFrom(typeOrdinal) : getDefaultType();
    }

    /** All the types of this kind of entry, in ordinal order. */
    protected abstract TransferType[] getTypes();

    @Override
    public boolean equals(Object o) {
        return o instanceof POTradeTransferEntry other && getClass() == other.getClass() && type == other.type
                && ItemStack.matches(filter, other.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type.ordinal(), filter.isEmpty() ? 0 : ItemStack.hashItemAndComponents(filter) * 31 + filter.getCount());
    }

    public interface TransferType {
        void doTransfer(IItemHandler storage, IItemHandler move, ItemStack filter);

        int ordinal();
    }
}
