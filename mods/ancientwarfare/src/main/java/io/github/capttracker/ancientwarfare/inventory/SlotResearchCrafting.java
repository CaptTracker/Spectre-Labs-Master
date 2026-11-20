/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/inventory/SlotResearchCrafting.
 */
package io.github.capttracker.ancientwarfare.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.event.EventHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The result slot of the Engineering Station. It is used instead of the vanilla result slot because the research
 * recipes' ingredients can ask for more than one item from a grid slot, which the vanilla slot (it takes exactly one
 * item per grid slot) cannot express. The recipe comes from the station's recipe memory, not from a recipe lookup
 * on the grid, because the station may show a recipe the grid does not complete (the items come from its storage).
 */
public class SlotResearchCrafting extends Slot {
    /**
     * Where the slot gets the recipe it is crafting. The Engineering Station menu implements this on top of its
     * recipe memory (1.12.2: {@code CraftingRecipeMemory}); the research recipe class of the recipes slice
     * decides how counts are stored, see {@link #getSizedIngredients}.
     */
    public interface RecipeSource {
        /** The recipe being crafted, or null if there is none. */
        @Nullable
        Recipe<CraftingInput> getRecipe();

        /**
         * The ingredients of the recipe with the number of items each takes from its grid slot (1.12.2:
         * {@code IIngredientCount}). The default takes one item per ingredient, like vanilla.
         */
        default List<SizedIngredient> getSizedIngredients(Recipe<CraftingInput> recipe) {
            List<SizedIngredient> ret = new ArrayList<>();
            for (Ingredient ingredient : recipe.getIngredients()) {
                ret.add(new SizedIngredient(ingredient, 1));
            }
            return ret;
        }
    }

    /** The craft matrix inventory linked to this result slot. */
    private final CraftingContainer craftMatrix;
    /** The player that is using the GUI where this slot resides. */
    private final Player player;
    /** The number of items that have been crafted so far. Gets passed to the crafting events before being reset. */
    private int amountCrafted;

    private final RecipeSource recipeSource;

    public SlotResearchCrafting(Player player, RecipeSource recipeSource, CraftingContainer craftingInventory, Container inventory,
                                int slotIndex, int xPosition, int yPosition) {
        super(inventory, slotIndex, xPosition, yPosition);
        this.player = player;
        this.craftMatrix = craftingInventory;
        this.recipeSource = recipeSource;
    }

    /** Nothing can be placed in a result slot. */
    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public ItemStack remove(int amount) {
        if (hasItem()) {
            amountCrafted += Math.min(amount, getItem().getCount());
        }
        return super.remove(amount);
    }

    /** @param stack the output, not the ingredients */
    @Override
    protected void onQuickCraft(ItemStack stack, int amount) {
        amountCrafted += amount;
        checkTakeAchievements(stack);
    }

    @Override
    protected void onSwapCraft(int numItemsCrafted) {
        amountCrafted += numItemsCrafted;
    }

    @Override
    protected void checkTakeAchievements(ItemStack stack) {
        if (amountCrafted > 0) {
            stack.onCraftedBy(player.level(), player, amountCrafted);
            EventHooks.firePlayerCraftingEvent(player, stack, craftMatrix);
        }
        if (container instanceof RecipeCraftingHolder holder) {
            holder.awardUsedRecipes(player, craftMatrix.getItems());
        }
        amountCrafted = 0;
    }

    @Override
    public void onTake(Player thePlayer, ItemStack stack) {
        checkTakeAchievements(stack);

        Recipe<CraftingInput> recipe = recipeSource.getRecipe();
        if (recipe == null) {
            return;
        }

        // The whole matrix, not trimmed to the used area, so that remaining item i belongs to grid slot i.
        CraftingInput input = craftMatrix.asCraftInput();
        CommonHooks.setCraftingPlayer(thePlayer);
        NonNullList<ItemStack> remainingItems = recipe.getRemainingItems(input);
        CommonHooks.setCraftingPlayer(null);

        List<SizedIngredient> ingredients = recipeSource.getSizedIngredients(recipe);
        List<Integer> usedIngredients = new ArrayList<>();
        for (int i = 0; i < remainingItems.size(); ++i) {
            ItemStack itemstack = craftMatrix.getItem(i);
            ItemStack remaining = remainingItems.get(i);
            if (!itemstack.isEmpty()) {
                // TODO(original): if two ingredients accept the same item with different counts, the first free one that
                //  matches is used, which may not be the one the recipe meant for this slot.
                int ingredientIndex = -1;
                for (int j = 0; j < ingredients.size(); j++) {
                    if (!usedIngredients.contains(j) && ingredients.get(j).test(itemstack)) {
                        ingredientIndex = j;
                        break;
                    }
                }
                usedIngredients.add(ingredientIndex);
                craftMatrix.removeItem(i, ingredientIndex >= 0 ? ingredients.get(ingredientIndex).count() : 1);
                itemstack = craftMatrix.getItem(i);
            }
            if (!remaining.isEmpty()) {
                if (itemstack.isEmpty()) {
                    craftMatrix.setItem(i, remaining);
                } else if (ItemStack.isSameItemSameComponents(itemstack, remaining)) {
                    remaining.grow(itemstack.getCount());
                    craftMatrix.setItem(i, remaining);
                } else if (!player.getInventory().add(remaining)) {
                    player.drop(remaining, false);
                }
            }
        }
    }

    @Override
    public boolean isFake() {
        return true;
    }
}
