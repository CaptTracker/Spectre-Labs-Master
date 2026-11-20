/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemFoodBundle.
 */
package io.github.capttracker.ancientwarfarenpc.item;

import net.minecraft.world.item.Item;

/**
 * The food bundle: 15 hunger, saturation modifier 1.0, wolf food. The food values are the {@code food} component set by the registration
 * ({@code AwNpcs.FOOD_BUNDLE}); the wolf food and the {@code ancientwarfarenpc:food_bundles} tags are data files (the ore name {@code foodBundle}).
 */
public class ItemFoodBundle extends Item {
    public ItemFoodBundle(Properties properties) {
        super(properties);
    }
}
