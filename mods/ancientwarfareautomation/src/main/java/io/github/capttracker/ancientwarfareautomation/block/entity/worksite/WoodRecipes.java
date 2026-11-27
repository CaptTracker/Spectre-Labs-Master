/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8): what a sawmill makes of a wood type, read from the crafting recipes.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Finds, from the vanilla crafting recipes (so modded woods work as well), the planks of a log and what a wood type's planks make: slabs, stairs,
 * fences, doors and sticks. The results are cached until the recipe manager changes (a data pack reload).
 */
final class WoodRecipes {
    /** Vanilla's stick recipe: 2 planks make 4 sticks. */
    static final int STICK_PLANKS = 2;
    static final int STICK_YIELD = 4;

    /** What one craft of a product takes and makes. */
    record Product(Item item, int yield, int planks, int sticks) {
        /** The planks it costs counting the sticks as planks, rounded up. */
        int plankCost() {
            return planks + (sticks * STICK_PLANKS + STICK_YIELD - 1) / STICK_YIELD;
        }
    }

    private static final Map<Item, Item> PLANKS_OF_LOG = new HashMap<>();
    private static final Map<String, Product> PRODUCTS = new HashMap<>();
    private static final Item NONE = Items.AIR;
    @Nullable
    private static RecipeManager cachedFor;

    private WoodRecipes() {}

    private static void check(Level level) {
        RecipeManager manager = level.getRecipeManager();
        if (manager != cachedFor) {
            PLANKS_OF_LOG.clear();
            PRODUCTS.clear();
            cachedFor = manager;
        }
    }

    /** The planks item that the log makes in the crafting grid, or null. */
    @Nullable
    static synchronized Item planksOf(Level level, ItemStack log) {
        check(level);
        Item cached = PLANKS_OF_LOG.get(log.getItem());
        if (cached != null) {
            return cached == NONE ? null : cached;
        }
        Item found = NONE;
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (!result.is(ItemTags.PLANKS)) {
                continue;
            }
            List<Ingredient> ingredients = recipe.getIngredients().stream().filter(i -> !i.isEmpty()).toList();
            if (ingredients.size() == 1 && ingredients.get(0).test(log)) {
                found = result.getItem();
                break;
            }
        }
        PLANKS_OF_LOG.put(log.getItem(), found);
        return found == NONE ? null : found;
    }

    /**
     * The recipe of a product made of the planks (and sticks): its result must match {@code resultMatcher}, and every ingredient must be the planks or,
     * if {@code withSticks}, a stick.
     */
    @Nullable
    static synchronized Product productOf(Level level, Item planks, String key, Predicate<ItemStack> resultMatcher, boolean withSticks) {
        check(level);
        String cacheKey = BuiltInRegistries.ITEM.getKey(planks) + "|" + key;
        if (PRODUCTS.containsKey(cacheKey)) {
            return PRODUCTS.get(cacheKey);
        }
        ItemStack planksStack = new ItemStack(planks);
        ItemStack stick = new ItemStack(Items.STICK);
        Product found = null;
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty() || !resultMatcher.test(result)) {
                continue;
            }
            int planksCount = 0;
            int sticksCount = 0;
            boolean valid = true;
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) {
                    continue;
                }
                if (ingredient.test(planksStack)) {
                    planksCount++;
                } else if (withSticks && ingredient.test(stick)) {
                    sticksCount++;
                } else {
                    valid = false;
                    break;
                }
            }
            if (valid && planksCount > 0) {
                found = new Product(result.getItem(), result.getCount(), planksCount, sticksCount);
                break;
            }
        }
        PRODUCTS.put(cacheKey, found);
        return found;
    }
}
