/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/crafting/wrappers/RegularCraftingWrapper.
 */
package io.github.capttracker.ancientwarfare.crafting.wrappers;

import io.github.capttracker.ancientwarfare.crafting.ICraftingRecipe;
import io.github.capttracker.ancientwarfare.crafting.RecipeResourceLocation;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** An ordinary crafting-table recipe (shaped, shapeless or special), seen through {@link ICraftingRecipe}. One item per ingredient. */
public final class RegularCraftingWrapper implements ICraftingRecipe {
    private final CraftingRecipe recipe;
    private final RecipeResourceLocation registryName;
    private final List<SizedIngredient> ingredients;

    public RegularCraftingWrapper(ResourceLocation id, CraftingRecipe recipe) {
        this.recipe = recipe;
        this.registryName = new RecipeResourceLocation(RecipeResourceLocation.Kind.REGULAR, id);
        List<SizedIngredient> list = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            list.add(ingredient.isEmpty() ? EMPTY_SLOT : new SizedIngredient(ingredient, 1));
        }
        this.ingredients = List.copyOf(list);
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public List<SizedIngredient> getIngredients() {
        return ingredients;
    }

    @Override
    public ItemStack getCraftingResult(CraftingInput input, HolderLookup.Provider registries) {
        return recipe.assemble(input, registries);
    }

    @Override
    public ItemStack getRecipeOutput(HolderLookup.Provider registries) {
        return recipe.getResultItem(registries);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return recipe.getRemainingItems(input);
    }

    @Override
    public RecipeResourceLocation getRegistryName() {
        return registryName;
    }

    @Override
    public Optional<ResourceLocation> getNeededResearch() {
        return Optional.empty();
    }

    @Override
    public int getRecipeWidth() {
        return recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
    }

    @Override
    public int getRecipeHeight() {
        return recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : 3;
    }

    @Override
    public Recipe<CraftingInput> getRecipe() {
        return recipe;
    }
}
