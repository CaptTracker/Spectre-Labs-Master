/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/crafting/ICraftingRecipe.
 */
package io.github.capttracker.ancientwarfare.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * One view over the two kinds of recipe the engineering bench and the other research crafting blocks work with:
 * research recipes (counted ingredients, gated by a research goal) and ordinary crafting recipes.
 *
 * <p>Ingredients are listed in the recipe's own layout (row by row, {@link #getRecipeWidth()} columns), with
 * {@link #EMPTY_SLOT} where the recipe leaves a cell empty. The index of an ingredient in this list is what the
 * inventory matching reports in {@link AWCraftingManager}.
 */
public interface ICraftingRecipe {
    /** Marks an empty cell of a recipe pattern. */
    SizedIngredient EMPTY_SLOT = new SizedIngredient(Ingredient.EMPTY, 1);

    static boolean isEmptySlot(SizedIngredient ingredient) {
        return ingredient.ingredient().isEmpty();
    }

    /** False only for the "no recipe" placeholder. */
    boolean isValid();

    List<SizedIngredient> getIngredients();

    ItemStack getCraftingResult(CraftingInput input, HolderLookup.Provider registries);

    ItemStack getRecipeOutput(HolderLookup.Provider registries);

    /** The items that stay behind after crafting, one entry per cell of {@code input}. */
    NonNullList<ItemStack> getRemainingItems(CraftingInput input);

    RecipeResourceLocation getRegistryName();

    /** The research goal the player must know, if the recipe is gated (1.12.2 returned the goal's name). */
    Optional<ResourceLocation> getNeededResearch();

    int getRecipeWidth();

    int getRecipeHeight();

    /** The wrapped Minecraft recipe, or null for the "no recipe" placeholder. */
    @Nullable
    Recipe<CraftingInput> getRecipe();
}
