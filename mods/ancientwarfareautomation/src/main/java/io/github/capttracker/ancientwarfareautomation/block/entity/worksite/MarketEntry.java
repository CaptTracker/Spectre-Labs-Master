/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): one line of a market stall's offer list.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One sell offer or buy order of a market stall: {@code item} (its count is the amount per deal), {@code price} (a coin stack: the coin and how many per
 * deal) and, for a buy order, {@code limit} (the items the stall will buy in all, 0 = no limit) with {@code remaining} (what is left of it).
 */
public final class MarketEntry {
    public static final String COIN_NAMESPACE = "ancientwarfarenpc";
    /** The coins in the order the screen cycles through them, cheapest last. */
    public static final List<String> METALS = List.of("ancient", "gold", "silver", "copper");
    public static final int MAX_PRICE = 999;
    public static final int MAX_LIMIT = 9999;

    public ItemStack item = ItemStack.EMPTY;
    public ItemStack price = ItemStack.EMPTY;
    public int limit = 0;
    public int remaining = 0;

    public MarketEntry() {}

    public MarketEntry copy() {
        MarketEntry entry = new MarketEntry();
        entry.item = item.copy();
        entry.price = price.copy();
        entry.limit = limit;
        entry.remaining = remaining;
        return entry;
    }

    /** True if the entry has an item and a price. */
    public boolean isComplete() {
        return !item.isEmpty() && !price.isEmpty() && isCoin(price);
    }

    public int amount() {
        return Math.max(1, item.getCount());
    }

    // ---- coins ----

    public static boolean isCoin(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals(COIN_NAMESPACE) && id.getPath().startsWith("coin_");
    }

    /** The coin stack of a metal (empty if the coin item is not registered). */
    public static ItemStack coin(String metal, int count) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(COIN_NAMESPACE, "coin_" + metal))
                .map(item -> new ItemStack(item, count)).orElse(ItemStack.EMPTY);
    }

    /** The metal name of a coin stack, or "" for anything else. */
    public static String metalOf(ItemStack stack) {
        if (!isCoin(stack)) {
            return "";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().substring("coin_".length());
    }

    /** The next (or previous) coin metal in the cycle; an unknown metal starts at the first. */
    public static String cycleMetal(String metal, boolean forward) {
        int index = METALS.indexOf(metal);
        if (index < 0) {
            return METALS.get(METALS.size() - 1);
        }
        return METALS.get((index + (forward ? 1 : METALS.size() - 1)) % METALS.size());
    }

    // ---- saving ----

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!item.isEmpty()) {
            tag.put("item", item.save(registries));
        }
        if (!price.isEmpty()) {
            tag.put("price", price.save(registries));
        }
        tag.putInt("limit", limit);
        tag.putInt("remaining", remaining);
        return tag;
    }

    public static MarketEntry load(CompoundTag tag, HolderLookup.Provider registries) {
        MarketEntry entry = new MarketEntry();
        entry.item = tag.contains("item", Tag.TAG_COMPOUND) ? ItemStack.parseOptional(registries, tag.getCompound("item")) : ItemStack.EMPTY;
        entry.price = tag.contains("price", Tag.TAG_COMPOUND) ? ItemStack.parseOptional(registries, tag.getCompound("price")) : ItemStack.EMPTY;
        entry.limit = tag.getInt("limit");
        entry.remaining = tag.getInt("remaining");
        return entry;
    }

    public static ListTag saveList(List<MarketEntry> entries, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (MarketEntry entry : entries) {
            list.add(entry.save(registries));
        }
        return list;
    }

    public static List<MarketEntry> loadList(ListTag list, HolderLookup.Provider registries, int max) {
        List<MarketEntry> entries = new ArrayList<>();
        for (int i = 0; i < list.size() && entries.size() < max; i++) {
            entries.add(load(list.getCompound(i), registries));
        }
        return entries;
    }

    /**
     * Makes an entry that came from a client safe: the item count 1..its max stack size, the price a coin with 1..{@value #MAX_PRICE}, the limit
     * 0..{@value #MAX_LIMIT}. An entry without an item, or with a price that is no coin, is emptied.
     */
    public void sanitize() {
        if (!item.isEmpty()) {
            item.setCount(Math.max(1, Math.min(item.getMaxStackSize(), item.getCount())));
        }
        if (!isCoin(price)) {
            price = ItemStack.EMPTY;
        } else {
            price.setCount(Math.max(1, Math.min(MAX_PRICE, price.getCount())));
        }
        limit = Math.max(0, Math.min(MAX_LIMIT, limit));
        remaining = limit == 0 ? 0 : Math.max(0, Math.min(limit, remaining));
    }
}
