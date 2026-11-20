/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/crafting/wrappers/NoRecipeWrapper.
 */
package io.github.capttracker.ancientwarfare.crafting.wrappers;

import io.github.capttracker.ancientwarfare.crafting.ICraftingRecipe;
import io.github.capttracker.ancientwarfare.crafting.RecipeResourceLocation;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Null object: the bench has no recipe selected. */
public final class NoRecipeWrapper implements ICraftingRecipe {
    public static final NoRecipeWrapper INSTANCE = new NoRecipeWrapper();

    private NoRecipeWrapper() {}

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public List<SizedIngredient> getIngredients() {
        return List.of();
    }

    @Override
    public ItemStack getCraftingResult(CraftingInput input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getRecipeOutput(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return NonNullList.create();
    }

    @Override
    public RecipeResourceLocation getRegistryName() {
        return RecipeResourceLocation.NO_RECIPE_REGISTRY_NAME;
    }

    @Override
    public Optional<ResourceLocation> getNeededResearch() {
        return Optional.empty();
    }

    @Override
    public int getRecipeWidth() {
        return 0;
    }

    @Override
    public int getRecipeHeight() {
        return 0;
    }

    @Nullable
    @Override
    public Recipe<CraftingInput> getRecipe() {
        return null;
    }
}
