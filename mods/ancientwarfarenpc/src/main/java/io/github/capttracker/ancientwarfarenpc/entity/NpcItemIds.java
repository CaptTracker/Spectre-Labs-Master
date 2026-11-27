/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: replaces the {@code instanceof ItemCommandBaton / ItemCombatOrder / ...} checks of the NPC entities,
 * which would make the entity slice depend on the class names of the item slice. The registry names are the 1.12.2 ones.
 */
package io.github.capttracker.ancientwarfarenpc.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Identifies the NPC module's items by registry name. */
public final class NpcItemIds {
    public static final String WORK_ORDER = "work_order";
    public static final String UPKEEP_ORDER = "upkeep_order";
    public static final String COMBAT_ORDER = "combat_order";
    public static final String ROUTING_ORDER = "routing_order";
    public static final String TRADE_ORDER = "trade_order";
    public static final String SEA_ROUTE = "sea_route";

    private NpcItemIds() {}

    private static boolean isNpcNamespace(ResourceLocation id) {
        // the NPC items live in ancientwarfarenpc; the Core slice's converters map that namespace to ancientwarfare in a few places
        return id.getNamespace().equals("ancientwarfarenpc") || id.getNamespace().equals("ancientwarfare");
    }

    /** Any of the five command batons ({@code wooden_command_baton} ... {@code diamond_command_baton}). */
    public static boolean isCommandBaton(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return isNpcNamespace(id) && id.getPath().endsWith("_command_baton");
    }

    /** True if the stack is the item with this registry path (one of the order constants). */
    public static boolean is(ItemStack stack, String path) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return isNpcNamespace(id) && id.getPath().equals(path);
    }
}
