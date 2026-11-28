/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): the kitchen of a cook, with the cooking pot and the cutting board of another mod (Farmer's Delight).
 */
package io.github.capttracker.ancientwarfarenpc.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.List;

/**
 * What a cook may ask of a kitchen: which block entities are cooking stations, which dishes and cuts the recipes allow, and how to put ingredients into a
 * station and take what comes out. The default does nothing (no kitchen), and is used when the other mod is not installed. The recipes are described with
 * plain vanilla types ({@link Ingredient}, {@link ItemStack}), so the cook goal itself names no class of the other mod. Server side.
 */
public interface KitchenCompat {
    KitchenCompat NONE = new KitchenCompat() {};

    /** The two kinds of station: a cooking pot (cooks by itself over a heat source) and a cutting board (cut with a tool). */
    enum Kind { POT, BOARD }

    /** A cooking recipe: one stack for each ingredient, what comes out, and the container it needs (a bowl, a bottle; empty for none). */
    record Dish(ResourceLocation id, List<Ingredient> ingredients, ItemStack result, ItemStack container) {}

    /** A cutting recipe: what is cut, the tool that cuts it and what it gives (the sure results; some recipes also give a chance of more). */
    record Cut(ResourceLocation id, Ingredient input, Ingredient tool, List<ItemStack> results) {}

    /** The state of a cooking pot: whether a heat source is under it, whether something is cooking (or waits in it), and what is ready to take. */
    record Pot(boolean heated, boolean busy, ItemStack output) {}

    /** The kind of station a block entity is, or null if it is none. */
    @Nullable
    default Kind kindOf(BlockEntity be) {
        return null;
    }

    /** The cooking recipes. */
    default List<Dish> dishes(ServerLevel level) {
        return List.of();
    }

    /** The cutting recipes. */
    default List<Cut> cuts(ServerLevel level) {
        return List.of();
    }

    /** The state of the pot at the position, or null if there is none. */
    @Nullable
    default Pot pot(ServerLevel level, BlockPos pos) {
        return null;
    }

    /** Puts the ingredients (and the container) into an empty, heated pot, which then cooks by itself. False if that is not possible. */
    default boolean fillPot(ServerLevel level, BlockPos pos, List<ItemStack> ingredients, ItemStack container) {
        return false;
    }

    /** Takes what the pot has ready (empty if nothing). */
    default ItemStack takePotOutput(ServerLevel level, BlockPos pos) {
        return ItemStack.EMPTY;
    }

    /** Whether the board at the position is there and empty. */
    default boolean boardFree(ServerLevel level, BlockPos pos) {
        return false;
    }

    /**
     * Cuts one item of the stack on the board with the tool (the tool wears). Returns what came out, or null if nothing was cut (the stack and the tool are then
     * untouched; the caller shrinks the stack only on a result).
     */
    @Nullable
    default List<ItemStack> cut(ServerLevel level, BlockPos pos, ItemStack input, ItemStack tool) {
        return null;
    }
}
