/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the slotClick request code (handleClientRequestSpecific / handlePacketData) of
 * ContainerWarehouseControl and ContainerWarehouseStorage, and the filter list code of the filter menus.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfare.util.NBTHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Reading and writing the messages the warehouse screens send, and checking them on the server. Nothing a client sends
 * is used as it came: items are parsed from NBT (an unknown item gives an empty stack), counts are clamped to what a
 * stack can hold and lists are cut to what the screen allows.
 */
final class WarehouseRequests {
    static final String SLOT_CLICK_TAG = "slotClick";
    static final String REQ_ITEM_TAG = "reqItem";
    static final String IS_SHIFT_CLICK_TAG = "isShiftClick";
    static final String IS_RIGHT_CLICK_TAG = "isRightClick";
    static final String FILTER_LIST_TAG = "filterList";

    private WarehouseRequests() {}

    /** A click on an item of the warehouse screen, as the server understood it. The item may be empty (a click on empty space). */
    record SlotClick(ItemStack item, boolean shift, boolean right) {}

    /** The message of a click: the item (at most one stack of it) and the modifier flags. */
    static CompoundTag writeSlotClick(HolderLookup.Provider registries, ItemStack stack, int count, boolean shift, boolean right) {
        CompoundTag tag = new CompoundTag();
        if (!stack.isEmpty()) {
            ItemStack copy = stack.copyWithCount(Math.max(1, Math.min(count, stack.getMaxStackSize())));
            tag.put(REQ_ITEM_TAG, copy.saveOptional(registries));
        }
        tag.putBoolean(IS_SHIFT_CLICK_TAG, shift);
        tag.putBoolean(IS_RIGHT_CLICK_TAG, right);
        CompoundTag packet = new CompoundTag();
        packet.put(SLOT_CLICK_TAG, tag);
        return packet;
    }

    /** Server: reads a click message; null if it is malformed. */
    @Nullable
    static SlotClick readSlotClick(HolderLookup.Provider registries, CompoundTag packet) {
        if (!packet.contains(SLOT_CLICK_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag reqTag = packet.getCompound(SLOT_CLICK_TAG);
        ItemStack item = ItemStack.EMPTY;
        if (reqTag.contains(REQ_ITEM_TAG, Tag.TAG_COMPOUND)) {
            item = ItemStack.parseOptional(registries, reqTag.getCompound(REQ_ITEM_TAG));
            if (!item.isEmpty()) {
                item.setCount(Mth.clamp(item.getCount(), 1, item.getMaxStackSize()));
            }
        }
        return new SlotClick(item, reqTag.getBoolean(IS_SHIFT_CLICK_TAG), reqTag.getBoolean(IS_RIGHT_CLICK_TAG));
    }

    /**
     * Server: a filter list from a client, cut to {@code maxFilters} entries. Each filter is still to be cleaned by the
     * caller (the item of a filter is a template and must have a count of one: {@link #template}).
     */
    static <T extends INBTSerializable<CompoundTag>> List<T> readFilters(HolderLookup.Provider registries, CompoundTag tag,
                                                                       Supplier<T> factory, int maxFilters) {
        List<T> all = NBTHelper.deserializeListFrom(registries, tag, FILTER_LIST_TAG, factory);
        return new ArrayList<>(all.size() > maxFilters ? all.subList(0, maxFilters) : all);
    }

    /** A filter item as it is stored: one item, or empty. */
    static ItemStack template(ItemStack stack) {
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
    }
}
