/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/crafting/ResearchRecipeBase.
 */
package io.github.capttracker.ancientwarfare.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.init.AwCoreCrafting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.ArrayList;
import java.util.List;

/**
 * A recipe for the engineering bench: counted ingredients (a grid cell can ask for more than one item, which is taken
 * from the bench's storage) and a research goal the crafter must know. They are a recipe type of their own, so
 * the crafting table never offers them.
 *
 * <p>The ingredients are {@link SizedIngredient}s (1.12.2: the {@code IIngredientCount} ingredient classes); a cell
 * only accepts a stack at least as big as its count, as before. Whether the goal is actually required depends on the
 * config, see {@link AWCraftingManager#requiresResearch}.
 */
public abstract class ResearchRecipe implements Recipe<CraftingInput> {
    /** The research id of a recipe file. A bare name means a goal of this mod: {@code "trade"} is {@code ancientwarfare:trade}. */
    public static final Codec<ResourceLocation> RESEARCH_ID_CODEC = Codec.STRING.comapFlatMap(text -> {
        ResourceLocation id = text.contains(":") ? ResourceLocation.tryParse(text) : ResourceLocation.tryBuild(AncientWarfareCore.MOD_ID, text);
        return id == null ? DataResult.error(() -> "Not a valid research id: " + text) : DataResult.success(id);
    }, ResourceLocation::toString);

    private final ResourceLocation research;
    private final ItemStack result;

    protected ResearchRecipe(ResourceLocation research, ItemStack result) {
        this.research = research;
        this.result = result;
    }

    public ResourceLocation research() {
        return research;
    }

    public ItemStack result() {
        return result;
    }

    /** Layout of the recipe, row by row ({@link #width()} columns), {@link ICraftingRecipe#EMPTY_SLOT} for empty cells. */
    public abstract List<SizedIngredient> slots();

    public abstract int width();

    public abstract int height();

    /** The non-empty ingredients. */
    public List<SizedIngredient> sizedIngredients() {
        List<SizedIngredient> ret = new ArrayList<>();
        for (SizedIngredient slot : slots()) {
            if (!ICraftingRecipe.isEmptySlot(slot)) {
                ret.add(slot);
            }
        }
        return ret;
    }

    /** Whether any ingredient asks for more than one item (1.12.2: any ingredient was an {@code IIngredientCount}). */
    public boolean hasCountIngredient() {
        return slots().stream().anyMatch(s -> !ICraftingRecipe.isEmptySlot(s) && s.count() > 1);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    /** Always empty: crafting research recipes never gives containers or tools back. */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return NonNullList.withSize(input.size(), ItemStack.EMPTY);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ret = NonNullList.create();
        for (SizedIngredient ingredient : sizedIngredients()) {
            ret.add(ingredient.ingredient());
        }
        return ret;
    }

    /** Keeps it out of the recipe book and the "recipe unlocked" toast. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public RecipeType<?> getType() {
        return AwCoreCrafting.RESEARCH_RECIPE_TYPE.get();
    }
}
