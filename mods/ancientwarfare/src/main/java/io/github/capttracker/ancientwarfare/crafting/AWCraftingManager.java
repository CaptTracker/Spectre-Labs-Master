/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/crafting/AWCraftingManager.
 */
package io.github.capttracker.ancientwarfare.crafting;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.config.AWCoreStatics;
import io.github.capttracker.ancientwarfare.crafting.wrappers.NoRecipeWrapper;
import io.github.capttracker.ancientwarfare.crafting.wrappers.RegularCraftingWrapper;
import io.github.capttracker.ancientwarfare.crafting.wrappers.ResearchCraftingWrapper;
import io.github.capttracker.ancientwarfare.init.AwCoreCrafting;
import io.github.capttracker.ancientwarfare.research.Research;
import io.github.capttracker.ancientwarfare.research.ResearchData;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Finds and matches the recipes of the research crafting blocks (engineering bench; the warehouse crafting station
 * and auto crafter of the automation module use it too).
 *
 * <p>What changed from 1.12.2: research recipes are an ordinary recipe type of the recipe manager, so the custom
 * registry, the loader and the ingredient factories are gone. Ingredients are {@link SizedIngredient}s. The research
 * check takes the researcher's UUID (read from the bound research book) and runs on the server.
 *
 * <p>The pooled matching (a recipe asks for 12 paper, the bench's storage holds it in several stacks) is the
 * original algorithm with these fixes, each of which only matters when the original misbehaved:
 * <ul>
 * <li>an ingredient asking for N items is satisfied by several partial stacks (the original refused any stack smaller
 * than N, which made its own remaining-count bookkeeping pointless);</li>
 * <li>the items to take out of the pool are reported exactly as they were found, so removal takes exactly N items of
 * the item types that were matched (the original reported one stack of the last item type found);</li>
 * <li>items reserved by one ingredient are not offered again to another, and the ingredient with the fewest
 * candidate stacks is served first.</li>
 * </ul>
 */
public final class AWCraftingManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int GRID_SIZE = 9;

    private AWCraftingManager() {}

    // ---- config and research gating ----

    /** Whether the recipe's output item is enabled in the config ({@code disabled_recipe_items}). */
    public static boolean isCraftable(ResearchRecipe recipe) {
        return AWCoreStatics.isItemCraftable(recipe.result().getItem());
    }

    /**
     * Whether crafting the recipe needs its research goal. 1.12.2 decided this when loading: with the research system
     * on, recipes of researchable items and every recipe with a counted ingredient were gated, every other recipe
     * was a plain recipe. The same rule is applied when asking, since the config can change.
     */
    public static boolean requiresResearch(ResearchRecipe recipe) {
        return AWCoreStatics.useResearchSystem
                && (AWCoreStatics.isItemResearcheable(recipe.result().getItem()) || recipe.hasCountIngredient());
    }

    /** Server side. True if the research system is off or the researcher knows the goal. */
    public static boolean canPlayerCraft(@Nullable MinecraftServer server, @Nullable UUID researcher, ResourceLocation research) {
        if (!AWCoreStatics.useResearchSystem) {
            return true;
        }
        return server != null && researcher != null && ResearchData.get(server).entry(researcher).knows(research);
    }

    /** A test for "does this researcher know that goal" on the given level's server. On a client it only passes if research is off. */
    public static Predicate<ResourceLocation> researchChecker(Level level, @Nullable UUID researcher) {
        MinecraftServer server = level.getServer();
        return goal -> canPlayerCraft(server, researcher, goal);
    }

    // ---- looking recipes up ----

    public static List<RecipeHolder<ResearchRecipe>> getResearchRecipes(RecipeManager recipes) {
        return recipes.getAllRecipesFor(AwCoreCrafting.RESEARCH_RECIPE_TYPE.get());
    }

    /** The enabled recipes gated by one goal (for the research book). */
    public static List<RecipeHolder<ResearchRecipe>> getRecipesForResearch(RecipeManager recipes, ResourceLocation research) {
        List<RecipeHolder<ResearchRecipe>> ret = new ArrayList<>();
        for (RecipeHolder<ResearchRecipe> holder : getResearchRecipes(recipes)) {
            if (holder.value().research().equals(research) && isCraftable(holder.value())) {
                ret.add(holder);
            }
        }
        return ret;
    }

    /**
     * Every research recipe and ordinary crafting recipe the grid completes. {@code input} should be the grid as
     * {@link CraftingInput#of} builds it (trimmed to the used area).
     *
     * @param hasResearch tells whether the crafter knows a research goal, see {@link #researchChecker}
     */
    public static List<ICraftingRecipe> findMatchingRecipes(CraftingInput input, Level level, Predicate<ResourceLocation> hasResearch) {
        List<ICraftingRecipe> ret = findMatchingResearchRecipes(input, level, hasResearch, true);
        ret.addAll(findMatchingRegularRecipes(input, level));
        return ret;
    }

    public static List<ICraftingRecipe> findMatchingRecipes(CraftingInput input, Level level, @Nullable UUID researcher) {
        return findMatchingRecipes(input, level, researchChecker(level, researcher));
    }

    private static List<ICraftingRecipe> findMatchingResearchRecipes(CraftingInput input, Level level, Predicate<ResourceLocation> hasResearch, boolean checkResearch) {
        List<ICraftingRecipe> ret = new ArrayList<>();
        for (RecipeHolder<ResearchRecipe> holder : getResearchRecipes(level.getRecipeManager())) {
            ResearchRecipe recipe = holder.value();
            if (!isCraftable(recipe) || !recipe.matches(input, level)) {
                continue;
            }
            if (!checkResearch || !requiresResearch(recipe) || hasResearch.test(recipe.research())) {
                ret.add(new ResearchCraftingWrapper(holder.id(), recipe));
            }
        }
        return ret;
    }

    private static List<ICraftingRecipe> findMatchingRegularRecipes(CraftingInput input, Level level) {
        List<ICraftingRecipe> ret = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (holder.value().matches(input, level)) {
                ret.add(new RegularCraftingWrapper(holder.id(), holder.value()));
            }
        }
        return ret;
    }

    private static List<ICraftingRecipe> findMatchingRecipesNoResearchCheck(CraftingInput input, Level level) {
        List<ICraftingRecipe> ret = findMatchingResearchRecipes(input, level, goal -> true, false);
        ret.addAll(findMatchingRegularRecipes(input, level));
        return ret;
    }

    /** Resolves a saved or received recipe name. Works on both sides (the client has the recipes too). */
    public static ICraftingRecipe getRecipe(RecipeResourceLocation name, Level level) {
        if (name.isNone()) {
            return NoRecipeWrapper.INSTANCE;
        }
        Optional<RecipeHolder<?>> holder = level.getRecipeManager().byKey(name.getResourceLocation());
        if (holder.isEmpty()) {
            return NoRecipeWrapper.INSTANCE;
        }
        Object recipe = holder.get().value();
        switch (name.getRecipeType()) {
            case REGULAR:
                if (recipe instanceof CraftingRecipe crafting) {
                    return new RegularCraftingWrapper(holder.get().id(), crafting);
                }
                break;
            case RESEARCH:
                if (recipe instanceof ResearchRecipe research) {
                    return new ResearchCraftingWrapper(holder.get().id(), research);
                }
                break;
            default:
                break;
        }
        return NoRecipeWrapper.INSTANCE;
    }

    /**
     * Builds a 3x3 recipe input from up to nine stacks. Like {@link CraftingInput#of} it is trimmed to the used area.
     * (1.12.2: {@code fillCraftingMatrixFromInventory}.)
     */
    public static CraftingInput fillCraftingMatrixFromInventory(List<ItemStack> resources) {
        List<ItemStack> grid = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            grid.add(i < resources.size() ? resources.get(i) : ItemStack.EMPTY);
        }
        return CraftingInput.of(3, 3, grid);
    }

    /**
     * The recipe a stored grid belongs to, ignoring research. If several match, the first one with the same output
     * item as {@code result} wins, else the first one. 1.12.2 compared ore dictionary ids when the output had any;
     * item tags are far too broad for that (almost every item has some), so the item itself is compared.
     */
    public static ICraftingRecipe findMatchingRecipe(Level level, List<ItemStack> inputs, ItemStack result) {
        CraftingInput input = fillCraftingMatrixFromInventory(inputs);
        List<ICraftingRecipe> recipes = findMatchingRecipesNoResearchCheck(input, level);
        if (recipes.isEmpty()) {
            return NoRecipeWrapper.INSTANCE;
        }
        if (recipes.size() == 1) {
            return recipes.get(0);
        }
        for (ICraftingRecipe recipe : recipes) {
            ItemStack other = recipe.getCraftingResult(input, level.registryAccess());
            if (!other.isEmpty() && ItemStack.isSameItem(other, result)) {
                return recipe;
            }
        }
        return recipes.get(0);
    }

    /** The items the recipe leaves behind that it would also accept as an ingredient (tools that survive crafting). */
    public static NonNullList<ItemStack> getReusableStacks(ICraftingRecipe recipe, CraftingInput input) {
        NonNullList<ItemStack> ret = NonNullList.create();
        List<SizedIngredient> ingredients = recipe.getIngredients();
        for (ItemStack remaining : recipe.getRemainingItems(input)) {
            if (!remaining.isEmpty() && ingredients.stream().anyMatch(i -> !ICraftingRecipe.isEmptySlot(i) && i.test(remaining))) {
                ret.add(remaining);
            }
        }
        return ret;
    }

    // ---- pooled ingredient matching ----

    /** What {@link #matchIngredients} found. */
    public static final class IngredientMatch {
        private final Map<Integer, List<ItemStack>> portions = new TreeMap<>();
        private final Map<Integer, ItemStack> representatives = new TreeMap<>();
        private final List<SizedIngredient> failed = new ArrayList<>();

        public boolean isSuccess() {
            return failed.isEmpty();
        }

        /** The ingredients nothing could be found for. Contains {@link ICraftingRecipe#EMPTY_SLOT} for an invalid recipe. */
        public List<SizedIngredient> failedIngredients() {
            return failed;
        }

        /** For each matched ingredient index, the exact stacks found for it; their counts add up to the ingredient's count. */
        public Map<Integer, List<ItemStack>> portions() {
            return portions;
        }

        /** Every stack found, ready to be taken out of the inventory that was searched. */
        public List<ItemStack> allPortions() {
            List<ItemStack> ret = new ArrayList<>();
            portions.values().forEach(ret::addAll);
            return ret;
        }

        /**
         * One stack per matched ingredient, at the ingredient's index (a nine slot list, so it fits a crafting
         * grid): the first item type found, with the ingredient's count. Empty if anything failed, like the original.
         */
        public NonNullList<ItemStack> representativeStacks() {
            if (!isSuccess()) {
                return NonNullList.create();
            }
            NonNullList<ItemStack> ret = NonNullList.withSize(GRID_SIZE, ItemStack.EMPTY);
            representatives.forEach((index, stack) -> {
                if (index < GRID_SIZE) {
                    ret.set(index, stack);
                }
            });
            return ret;
        }
    }

    /** Callbacks for {@link #getRecipeInventoryMatch(ICraftingRecipe, List, IItemHandler, Supplier, MatchVisitor)}. */
    public interface MatchVisitor<T> {
        void onMatch(T state, int ingredientIndex, ItemStack stack);

        void onFail(T state, SizedIngredient ingredient);
    }

    /**
     * Finds the items for every ingredient of the recipe in the inventory (nothing is removed).
     *
     * @param exactStacks what the player put in the grid: when the inventory holds enough of those exact items, they
     *                    are used for the ingredients they fit; every other ingredient takes any matching item
     */
    public static IngredientMatch matchIngredients(ICraftingRecipe recipe, List<ItemStack> exactStacks, IItemHandler inventory) {
        if (!recipe.isValid()) {
            IngredientMatch match = new IngredientMatch();
            match.failed.add(ICraftingRecipe.EMPTY_SLOT);
            return match;
        }
        return matchIngredientList(recipe.getIngredients(), exactStacks, inventory);
    }

    /** The same for a bare ingredient list, which may contain {@link ICraftingRecipe#EMPTY_SLOT} entries (they are skipped). */
    public static IngredientMatch matchIngredientList(List<SizedIngredient> ingredients, List<ItemStack> exactStacks, IItemHandler inventory) {
        IngredientMatch match = new IngredientMatch();
        List<ItemStack> pool = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                pool.add(stack.copy());
            }
        }
        Set<Integer> unmet = new LinkedHashSet<>();
        for (int i = 0; i < ingredients.size(); i++) {
            if (!ICraftingRecipe.isEmptySlot(ingredients.get(i))) {
                unmet.add(i);
            }
        }
        matchExactStacks(ingredients, exactStacks, pool, unmet, match);
        matchRemaining(ingredients, pool, unmet, match);
        return match;
    }

    private static final class ExactGroup {
        private final ItemStack key;
        private int total = 0;
        private final Set<Integer> indexes = new LinkedHashSet<>();

        private ExactGroup(ItemStack key) {
            this.key = key;
        }
    }

    /**
     * Grid stacks are assigned to the first ingredient that accepts them, grouped by item, and used if the pool holds
     * enough of that item for the whole group. Grid stacks that fit no ingredient are ignored (the original asked the
     * pool for one more of them, which only happens for grids that do not complete the recipe).
     */
    private static void matchExactStacks(List<SizedIngredient> ingredients, List<ItemStack> exactStacks, List<ItemStack> pool, Set<Integer> unmet, IngredientMatch match) {
        List<ExactGroup> groups = new ArrayList<>();
        Set<Integer> assigned = new HashSet<>();
        for (ItemStack stack : exactStacks) {
            if (stack.isEmpty()) {
                continue;
            }
            int index = -1;
            for (int i = 0; i < ingredients.size() && index < 0; i++) {
                SizedIngredient ingredient = ingredients.get(i);
                if (!assigned.contains(i) && !ICraftingRecipe.isEmptySlot(ingredient) && ingredient.test(stack)) {
                    index = i;
                }
            }
            if (index < 0) {
                continue;
            }
            assigned.add(index);
            ExactGroup group = null;
            for (ExactGroup existing : groups) {
                if (InventoryTools.doItemStacksMatch(existing.key, stack)) {
                    group = existing;
                    break;
                }
            }
            if (group == null) {
                group = new ExactGroup(stack.copyWithCount(1));
                groups.add(group);
            }
            group.total += ingredients.get(index).count();
            group.indexes.add(index);
        }
        for (ExactGroup group : groups) {
            if (countInPool(pool, group.key) < group.total) {
                continue;
            }
            reserve(pool, group.key, group.total);
            for (int index : group.indexes) {
                int count = ingredients.get(index).count();
                match.portions.put(index, List.of(group.key.copyWithCount(count)));
                match.representatives.put(index, group.key.copyWithCount(count));
                unmet.remove(index);
            }
        }
    }

    private static void matchRemaining(List<SizedIngredient> ingredients, List<ItemStack> pool, Set<Integer> unmet, IngredientMatch match) {
        List<Integer> order = new ArrayList<>(unmet);
        // most constrained ingredient first, so a flexible one does not take the only item a picky one accepts
        order.sort((a, b) -> Integer.compare(candidates(ingredients.get(a), pool), candidates(ingredients.get(b), pool)));
        for (int index : order) {
            SizedIngredient ingredient = ingredients.get(index);
            int needed = ingredient.count();
            List<ItemStack> parts = new ArrayList<>();
            for (ItemStack stack : pool) {
                if (needed <= 0) {
                    break;
                }
                if (stack.isEmpty() || !ingredient.ingredient().test(stack)) {
                    continue;
                }
                int taken = Math.min(needed, stack.getCount());
                parts.add(stack.copyWithCount(taken));
                stack.shrink(taken);
                needed -= taken;
            }
            if (needed > 0) {
                // give back what was reserved for the unfinished ingredient
                for (ItemStack part : parts) {
                    returnToPool(pool, part);
                }
                match.failed.add(ingredient);
            } else {
                match.portions.put(index, parts);
                match.representatives.put(index, parts.get(0).copyWithCount(ingredient.count()));
            }
        }
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

    private static int countInPool(List<ItemStack> pool, ItemStack key) {
        int count = 0;
        for (ItemStack stack : pool) {
            if (!stack.isEmpty() && InventoryTools.doItemStacksMatch(stack, key)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void reserve(List<ItemStack> pool, ItemStack key, int count) {
        int left = count;
        for (ItemStack stack : pool) {
            if (left <= 0) {
                return;
            }
            if (!stack.isEmpty() && InventoryTools.doItemStacksMatch(stack, key)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
    }

    private static void returnToPool(List<ItemStack> pool, ItemStack part) {
        for (ItemStack stack : pool) {
            if (ItemStack.isSameItemSameComponents(stack, part)) {
                stack.grow(part.getCount());
                return;
            }
        }
        pool.add(part.copy());
    }

    /** A nine slot list of one stack per ingredient index, or an empty list if the inventory cannot supply the recipe. */
    public static NonNullList<ItemStack> getRecipeInventoryMatch(ICraftingRecipe recipe, IItemHandler inventory) {
        return getRecipeInventoryMatch(recipe, List.of(), inventory);
    }

    public static NonNullList<ItemStack> getRecipeInventoryMatch(ICraftingRecipe recipe, List<ItemStack> exactStacks, IItemHandler inventory) {
        return matchIngredients(recipe, exactStacks, inventory).representativeStacks();
    }

    public static <T> T getRecipeInventoryMatch(ICraftingRecipe recipe, IItemHandler inventory, Supplier<T> initialize, MatchVisitor<T> visitor) {
        return getRecipeInventoryMatch(recipe, List.of(), inventory, initialize, visitor);
    }

    /** Reports every matched ingredient and every unmet one to the visitor (used to highlight missing items). */
    public static <T> T getRecipeInventoryMatch(ICraftingRecipe recipe, List<ItemStack> exactStacks, IItemHandler inventory,
                                                Supplier<T> initialize, MatchVisitor<T> visitor) {
        T state = initialize.get();
        IngredientMatch match = matchIngredients(recipe, exactStacks, inventory);
        match.representatives.forEach((index, stack) -> visitor.onMatch(state, index, stack));
        match.failed.forEach(ingredient -> visitor.onFail(state, ingredient));
        return state;
    }

    /**
     * Finds one ingredient in a cloned inventory and removes it from the clone, returning what was taken (empty if
     * the ingredient cannot be supplied). Several stacks of the same item can supply one ingredient.
     */
    public static ItemStack getIngredientInventoryMatch(IItemHandler clonedResourceInventory, SizedIngredient ingredient) {
        int count = ingredient.count();
        for (int slot = 0; slot < clonedResourceInventory.getSlots(); slot++) {
            ItemStack resourceStack = clonedResourceInventory.getStackInSlot(slot);
            if (resourceStack.isEmpty()) {
                continue;
            }
            // test with the proper count so the ingredient does not reject a stack that is only part of the amount
            if (ingredient.test(resourceStack.copyWithCount(count))) {
                ItemStack removed = InventoryTools.removeItems(clonedResourceInventory, resourceStack, count, true);
                if (removed.getCount() == count) {
                    InventoryTools.removeItems(clonedResourceInventory, resourceStack, count, false);
                    return removed;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Takes all of the ingredients out of the inventory, or nothing if it cannot supply them all (what
     * {@code ResearchGoal.tryStart} needs for the research resources).
     */
    public static boolean tryTakeIngredients(IItemHandler inventory, List<SizedIngredient> ingredients) {
        IngredientMatch match = matchIngredientList(ingredients, List.of(), inventory);
        if (!match.isSuccess()) {
            return false;
        }
        for (ItemStack portion : match.allPortions()) {
            extractExact(inventory, portion, portion.getCount());
        }
        return true;
    }

    /** Removes exactly {@code count} items of the prototype's item and components (counts spread over several slots), returns how many were removed. */
    public static int extractExact(IItemHandler handler, ItemStack prototype, int count) {
        int removed = 0;
        for (int slot = 0; slot < handler.getSlots() && removed < count; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, prototype)) {
                removed += handler.extractItem(slot, count - removed, false).getCount();
            }
        }
        return removed;
    }

    // ---- checks ----

    /**
     * Logs recipes that cannot work: an unknown research goal (1.12.2 threw while loading), or an ingredient that
     * has no items (an empty or missing tag). Called when a server has started.
     */
    public static void validateRecipes(MinecraftServer server) {
        var goals = Research.goals(server);
        Map<ResourceLocation, String> problems = new LinkedHashMap<>();
        for (RecipeHolder<ResearchRecipe> holder : getResearchRecipes(server.getRecipeManager())) {
            ResearchRecipe recipe = holder.value();
            if (!goals.containsKey(recipe.research())) {
                problems.put(holder.id(), "unknown research goal " + recipe.research());
            }
            for (SizedIngredient ingredient : recipe.sizedIngredients()) {
                if (ingredient.ingredient().hasNoItems()) {
                    problems.put(holder.id(), "an ingredient has no items: " + ingredient);
                    break;
                }
            }
        }
        problems.forEach((id, problem) -> LOGGER.warn("Research recipe {} cannot be crafted: {}", id, problem));
    }
}
