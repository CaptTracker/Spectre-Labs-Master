/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/crafting/wrappers/ResearchCraftingWrapper.
 */
package io.github.capttracker.ancientwarfare.crafting.wrappers;

import io.github.capttracker.ancientwarfare.crafting.ICraftingRecipe;
import io.github.capttracker.ancientwarfare.crafting.RecipeResourceLocation;
import io.github.capttracker.ancientwarfare.crafting.ResearchRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.List;
import java.util.Optional;

/** A research recipe seen through {@link ICraftingRecipe}. Crafting it returns no remaining items, as in 1.12.2 (no buckets or tools come back). */
public final class ResearchCraftingWrapper implements ICraftingRecipe {
    private final ResearchRecipe recipe;
    private final RecipeResourceLocation registryName;

    public ResearchCraftingWrapper(ResourceLocation id, ResearchRecipe recipe) {
        this.recipe = recipe;
        this.registryName = new RecipeResourceLocation(RecipeResourceLocation.Kind.RESEARCH, id);
    }

    public ResearchRecipe researchRecipe() {
        return recipe;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public List<SizedIngredient> getIngredients() {
        return recipe.slots();
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
        return Optional.of(recipe.research());
    }

    @Override
    public int getRecipeWidth() {
        return recipe.width();
    }

    @Override
    public int getRecipeHeight() {
        return recipe.height();
    }

    @Override
    public Recipe<CraftingInput> getRecipe() {
        return recipe;
    }
}
