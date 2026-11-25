/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/tile/CraftingRecipeMemory.
 */
package io.github.capttracker.ancientwarfare.crafting;

import io.github.capttracker.ancientwarfare.crafting.wrappers.NoRecipeWrapper;
import io.github.capttracker.ancientwarfare.item.ResearchBookItem;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a crafting block remembers: the research book in its book slot, the recipe grid, and the chosen recipe.
 * Owned by a block entity (the engineering bench; the warehouse crafting station of the automation module uses the
 * same object), which passes a callback to be told when anything changes.
 *
 * <p>The chosen recipe is kept as a name only. It is turned into a recipe on demand ({@link #getRecipe}), because a
 * block entity is loaded before the level and its recipes are available. The result item is not stored: it follows
 * from the recipe and the grid, and the menu computes it.
 */
public class CraftingRecipeMemory {
    private final Runnable onChanged;
    private RecipeResourceLocation recipe = RecipeResourceLocation.NO_RECIPE_REGISTRY_NAME;

    /** Holds a research book that has been bound to a researcher; nothing else is accepted. */
    public final ItemStackHandler bookSlot = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return ResearchBookItem.getResearcher(stack) != null;
        }

        @Override
        protected void onContentsChanged(int slot) {
            CraftingRecipeMemory.this.onChanged.run();
        }
    };

    /** The 3x3 recipe template. */
    public final CraftMatrix craftMatrix = new CraftMatrix();

    public CraftingRecipeMemory(Runnable onChanged) {
        this.onChanged = onChanged;
        this.craftMatrix.addListener(container -> onChanged.run());
    }

    public List<ItemStack> getCraftingStacks() {
        return new ArrayList<>(craftMatrix.getItems());
    }

    /** The grid as recipe input (trimmed to the used area, as recipes expect). */
    public CraftingInput getCraftingInput() {
        return CraftingInput.of(3, 3, craftMatrix.getItems());
    }

    @Nullable
    public ResearchBookItem.Researcher getResearcher() {
        return ResearchBookItem.getResearcher(bookSlot.getStackInSlot(0));
    }

    /** Whose research gates the recipes (the player the book is bound to), or null without a bound book. */
    @Nullable
    public UUID getResearcherId() {
        ResearchBookItem.Researcher researcher = getResearcher();
        return researcher == null ? null : researcher.id();
    }

    /** 1.12.2 {@code getCrafterName}: the name of the bound player. */
    @Nullable
    public String getCrafterName() {
        ResearchBookItem.Researcher researcher = getResearcher();
        return researcher == null ? null : researcher.name();
    }

    public RecipeResourceLocation getRecipeId() {
        return recipe;
    }

    public void setRecipeId(RecipeResourceLocation id) {
        if (!recipe.equals(id)) {
            recipe = id;
            onChanged.run();
        }
    }

    public void setRecipe(ICraftingRecipe newRecipe) {
        setRecipeId(newRecipe.getRegistryName());
    }

    /** The chosen recipe, or {@link NoRecipeWrapper#INSTANCE}. */
    public ICraftingRecipe getRecipe(Level level) {
        return recipe.isNone() ? NoRecipeWrapper.INSTANCE : AWCraftingManager.getRecipe(recipe, level);
    }

    /** Drops the book and the grid items (the template items are real items in 1.12.2 as well). */
    public void dropInventory(Level level, BlockPos pos) {
        InventoryTools.dropItemsInWorld(level, bookSlot, pos);
        InventoryTools.dropItemsInWorld(level, craftMatrix, pos);
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("bookSlot", bookSlot.serializeNBT(registries));
        tag.put("craftMatrix", InventoryTools.writeInventoryToNBT(craftMatrix, new CompoundTag(), registries));
        tag.putString("recipe", recipe.toString());
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        bookSlot.deserializeNBT(registries, tag.getCompound("bookSlot"));
        craftMatrix.clearContent();
        InventoryTools.readInventoryFromNBT(craftMatrix, tag.getCompound("craftMatrix"), registries);
        recipe = RecipeResourceLocation.deserialize(tag.getString("recipe"));
    }
}
