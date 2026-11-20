/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemCoin.CoinMetal (the enum only; the NPC module owns the coin item).
 */
package io.github.capttracker.ancientwarfarestructure.decor;

import io.github.capttracker.lib.registry.Ids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * The four coin metals. In 1.12.2 there was one {@code ancientwarfarenpc:coin} item with the metal in item NBT; the
 * NPC port splits it into one item per metal. The Structures module may not depend on the NPC module, so the coin
 * items are found through the item registry by {@link #coinItemId()} and every use copes with them being absent.
 */
public enum CoinMetal {
    ANCIENT("ancient", 0x445948),
    GOLD("gold", 0xFFD700),
    SILVER("silver", 0xC0C0C0),
    COPPER("copper", 0xB87333);

    /** Namespace of the module that registers the coin items. */
    public static final String COIN_NAMESPACE = "ancientwarfarenpc";

    private final String name;
    private final int color;

    CoinMetal(String name, int color) {
        this.name = name;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public int getColor() {
        return color;
    }

    /** Registry id of this metal's coin item in the NPC module ({@code ancientwarfarenpc:coin_<metal>}). */
    public ResourceLocation coinItemId() {
        return Ids.of(COIN_NAMESPACE, "coin_" + name);
    }

    /** The coin item, or empty when the NPC module is not loaded. */
    public Optional<Item> coinItem() {
        return BuiltInRegistries.ITEM.getOptional(coinItemId());
    }

    /** True when the stack is this metal's coin (1.12.2: ItemCoin.isSpecificCoin). */
    public boolean isCoin(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(coinItemId());
    }

    @Nullable
    public static CoinMetal byName(String name) {
        for (CoinMetal metal : values()) {
            if (metal.name.equals(name)) {
                return metal;
            }
        }
        return null;
    }
}
