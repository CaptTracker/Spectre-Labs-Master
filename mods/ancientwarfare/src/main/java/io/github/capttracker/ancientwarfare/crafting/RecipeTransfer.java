/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/compat/jei/PacketTransferRecipe and the item matching of MultiRecipeTransferHandler.
 */
package io.github.capttracker.ancientwarfare.crafting;

import io.github.capttracker.ancientwarfare.util.InventoryTools;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * "Move the recipe's ingredients into the crafting grid" for a menu with an {@link ICraftingContainer}. This is what a
 * recipe viewer's "+" button asks for. It has no recipe viewer types in it: the JEI handler (client) uses
 * {@link #plan} to check and highlight what is missing, and the payload handler (server) uses {@link #execute}.
 *
 * <p>Each ingredient is supplied by one item type (so a grid cell holds one stack), taken from the menu's inventories
 * plus whatever is already in the grid. With {@code maxTransfer} every cell gets as many multiples of the
 * ingredient's count as the inventories and the stack limit allow.
 */
public final class RecipeTransfer {
    private static final int GRID_WIDTH = 3;
    private static final int GRID_SIZE = 9;

    private RecipeTransfer() {}

    /** What {@link #plan} found: one stack per ingredient index (EMPTY for empty slots), or the indexes that could not be supplied. */
    public record Plan(List<ItemStack> stacks, List<Integer> missing, int multiplier) {
        public boolean isSuccess() {
            return missing.isEmpty();
        }
    }

    /** False for recipes that cannot be laid out in the 3x3 grid. */
    public static boolean fitsGrid(ICraftingRecipe recipe) {
        int width = recipe.getRecipeWidth();
        int size = recipe.getIngredients().size();
        return recipe.isValid() && width >= 1 && width <= GRID_WIDTH && size <= GRID_SIZE && (size - 1) / width < 3;
    }

    /** The grid slot (0-8) the ingredient with the given index goes to. */
    public static int gridSlot(ICraftingRecipe recipe, int ingredientIndex) {
        return ingredientIndex % recipe.getRecipeWidth() + ingredientIndex / recipe.getRecipeWidth() * GRID_WIDTH;
    }

    /** All non-empty stacks of the inventories plus the grid's stacks (the grid's items are given back before the transfer). */
    public static List<ItemStack> pool(ICraftingContainer container) {
        List<ItemStack> pool = new ArrayList<>();
        for (IItemHandler handler : container.getInventories()) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    pool.add(stack.copy());
                }
            }
        }
        for (ItemStack stack : container.getCraftingMemoryContainer().getMemory().craftMatrix.getItems()) {
            if (!stack.isEmpty()) {
                pool.add(stack.copy());
            }
        }
        return pool;
    }

    /**
     * Picks the items for the recipe from the pool (nothing is changed). If the recipe cannot be supplied, the
     * result lists the ingredient indexes that are missing for a single set.
     *
     * @param maxTransfer use the largest multiple of the ingredient counts that is possible, else exactly one
     */
    public static Plan plan(ICraftingRecipe recipe, List<ItemStack> pool, boolean maxTransfer) {
        List<SizedIngredient> ingredients = recipe.getIngredients();
        int maxCount = 1;
        for (SizedIngredient ingredient : ingredients) {
            if (!ICraftingRecipe.isEmptySlot(ingredient)) {
                maxCount = Math.max(maxCount, ingredient.count());
            }
        }
        for (int multiplier = maxTransfer ? Math.max(1, 64 / maxCount) : 1; multiplier > 1; multiplier--) {
            Plan plan = planFor(ingredients, pool, multiplier);
            if (plan.isSuccess()) {
                return plan;
            }
        }
        return planFor(ingredients, pool, 1);
    }

    private static Plan planFor(List<SizedIngredient> ingredients, List<ItemStack> originalPool, int multiplier) {
        List<ItemStack> pool = new ArrayList<>();
        originalPool.forEach(s -> pool.add(s.copy()));
        List<ItemStack> result = new ArrayList<>();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            result.add(ItemStack.EMPTY);
            if (!ICraftingRecipe.isEmptySlot(ingredients.get(i))) {
                order.add(i);
            }
        }
        // the most constrained ingredient first, so a flexible one does not take the only item a picky one accepts
        order.sort(Comparator.comparingInt(i -> candidates(ingredients.get(i), pool)));
        List<Integer> missing = new ArrayList<>();
        for (int index : order) {
            SizedIngredient ingredient = ingredients.get(index);
            int needed = ingredient.count() * multiplier;
            ItemStack chosen = ItemStack.EMPTY;
            for (ItemStack candidate : pool) {
                if (candidate.isEmpty() || !ingredient.ingredient().test(candidate)) {
                    continue;
                }
                if (needed <= candidate.getMaxStackSize() && countOf(pool, candidate) >= needed) {
                    chosen = candidate.copyWithCount(needed);
                    break;
                }
            }
            if (chosen.isEmpty()) {
                missing.add(index);
            } else {
                reserve(pool, chosen);
                result.set(index, chosen);
            }
        }
        missing.sort(Comparator.naturalOrder());
        return new Plan(result, missing, multiplier);
    }

    private static int candidates(SizedIngredient ingredient, List<ItemStack> pool) {
        int count = 0;
        for (ItemStack stack : pool) {
            if (!stack.isEmpty() && ingredient.ingredient().test(stack)) {
                count++;
            }
        }
        return count;
    }

    private static int countOf(List<ItemStack> pool, ItemStack key) {
        int count = 0;
        for (ItemStack stack : pool) {
            if (ItemStack.isSameItemSameComponents(stack, key)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void reserve(List<ItemStack> pool, ItemStack chosen) {
        int left = chosen.getCount();
        for (ItemStack stack : pool) {
            if (left <= 0) {
                return;
            }
            if (ItemStack.isSameItemSameComponents(stack, chosen)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
    }

    /** True if the grid's items would fit into the inventories (the first step of a transfer). */
    public static boolean gridFitsInventories(ICraftingContainer container) {
        List<ItemStack> grid = new ArrayList<>();
        for (ItemStack stack : container.getCraftingMemoryContainer().getMemory().craftMatrix.getItems()) {
            if (!stack.isEmpty()) {
                grid.add(stack.copy());
            }
        }
        return InventoryTools.insertItems(new CombinedInvWrapper(container.getInventories()), grid, true).isEmpty();
    }

    /**
     * Why the crafter of this menu may not use the recipe, as a lang key, or null if there is no problem. Without a
     * server (the client) only the missing research book is detected; the server checks the research itself.
     */
    @Nullable
    public static String researchProblem(ICraftingContainer container, ICraftingRecipe recipe, @Nullable MinecraftServer server) {
        if (recipe.getNeededResearch().isEmpty() || !(recipe.getRecipe() instanceof ResearchRecipe research)
                || !AWCraftingManager.requiresResearch(research)) {
            return null;
        }
        UUID crafter = container.getCraftingMemoryContainer().getMemory().getResearcherId();
        if (crafter == null) {
            return "jei.tooltip.error.recipe.transfer.no.research_book";
        }
        if (server != null && !AWCraftingManager.canPlayerCraft(server, crafter, recipe.getNeededResearch().get())) {
            return "jei.tooltip.error.recipe.transfer.missing.research";
        }
        return null;
    }

    /**
     * Server: gives the grid's items back to the inventories, takes the recipe's ingredients out of them into the
     * grid and chooses the recipe. Nothing changes if the recipe cannot be supplied or the grid's items do not fit.
     *
     * @return true if the grid was filled
     */
    public static boolean execute(ICraftingContainer container, ServerPlayer player, ICraftingRecipe recipe, boolean maxTransfer) {
        if (!fitsGrid(recipe)) {
            return false;
        }
        String problem = researchProblem(container, recipe, player.getServer());
        if (problem != null) {
            player.displayClientMessage(Component.translatable(problem), true);
            return false;
        }
        Plan plan = plan(recipe, pool(container), maxTransfer);
        if (!plan.isSuccess()) {
            player.displayClientMessage(Component.translatable("jei.tooltip.error.recipe.transfer.missing"), true);
            return false;
        }
        if (!container.pushCraftingMatrixToInventories()) {
            player.displayClientMessage(Component.translatable("jei.tooltip.error.recipe.transfer.inventory.full"), true);
            return false;
        }
        IItemHandlerModifiable inventories = new CombinedInvWrapper(container.getInventories());
        CraftingRecipeMemory memory = container.getCraftingMemoryContainer().getMemory();
        for (int i = 0; i < plan.stacks().size(); i++) {
            ItemStack stack = plan.stacks().get(i);
            if (stack.isEmpty()) {
                continue;
            }
            int removed = AWCraftingManager.extractExact(inventories, stack, stack.getCount());
            if (removed > 0) {
                memory.craftMatrix.setItem(gridSlot(recipe, i), stack.copyWithCount(removed));
            }
        }
        container.getCraftingMemoryContainer().setRecipe(recipe, true);
        return true;
    }
}
