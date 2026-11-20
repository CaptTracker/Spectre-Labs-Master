/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/inventory/ItemHashEntry.
 */
package io.github.capttracker.ancientwarfare.inventory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Function;

/**
 * Lightweight wrapper for an item stack as a hashable object suitable for use as keys in maps.
 * Uses item and data components for equality and hash code (1.12.2: item, damage and NBT tag).
 * Ignores quantity. Immutable.
 */
public final class ItemHashEntry {
    private final ItemStack stack;
    private String cachedNameAndTooltip = "";

    /** @param item the stack to key on; it is copied with a count of one */
    public ItemHashEntry(ItemStack item) {
        this.stack = item.copyWithCount(1);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(stack);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof ItemHashEntry other)) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(stack, other.stack);
    }

    /** The cached single-item stack. Callers must copy it before changing it. */
    public ItemStack getItemStack() {
        return stack;
    }

    /**
     * Lower-case display name plus tooltip text, for search boxes. Client code supplies the tooltip lines,
     * for example {@code s -> s.getTooltipLines(Item.TooltipContext.of(level), player, TooltipFlag.NORMAL)}.
     */
    public String getNameAndTooltip(Function<ItemStack, List<Component>> tooltipLines) {
        if (cachedNameAndTooltip.isEmpty()) {
            StringBuilder text = new StringBuilder(stack.getHoverName().getString().toLowerCase()).append(' ');
            for (Component line : tooltipLines.apply(stack)) {
                text.append(line.getString().toLowerCase()).append(' ');
            }
            cachedNameAndTooltip = text.toString();
        }
        return cachedNameAndTooltip;
    }

    public CompoundTag writeToNBT(HolderLookup.Provider registries) {
        Tag tag = stack.saveOptional(registries);
        return tag instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    /** An entry holding {@link ItemStack#EMPTY} if the item no longer exists. */
    public static ItemHashEntry readFromNBT(HolderLookup.Provider registries, CompoundTag tag) {
        return new ItemHashEntry(ItemStack.parseOptional(registries, tag));
    }
}
