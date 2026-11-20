/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/TileAutoCrafting.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.crafting.AWCraftingManager;
import io.github.capttracker.ancientwarfare.crafting.CraftingRecipeMemory;
import io.github.capttracker.ancientwarfare.crafting.ICraftingRecipe;
import io.github.capttracker.ancientwarfare.crafting.RecipeResourceLocation;
import io.github.capttracker.ancientwarfare.crafting.wrappers.NoRecipeWrapper;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The auto crafting table ({@code TileAutoCrafting}, work type crafting). It holds a recipe in a
 * {@link CraftingRecipeMemory} (the 3x3 template, the chosen recipe and a research book slot; the recipe is chosen
 * in its screen, by the GUI slice), takes the ingredients from the 18 slot resource inventory and puts the result and the
 * items the recipe leaves behind into the 9 slot output inventory. The action is available while the output can hold
 * the result. One craft costs one work unit.
 *
 * <p>Item handler: {@code null} side none, the bottom ({@link Direction#DOWN}) is the output inventory, every other
 * side the resource inventory (registered by {@link #registerCapabilities}).
 *
 * <p>Items a recipe leaves behind that it would accept as an ingredient again (tools that survive crafting) are
 * handled like the original did: they count as available (the pooled match includes them) and are not used up nor
 * given out. A remainder that equals an item that was just used up as an ingredient goes back into the resource
 * inventory (a container that is used again), every other remainder goes into the output.
 *
 * <p>Not synced to the client: the original sent the recipe memory in its update tag; the menu of the GUI slice
 * synchronises the template and the recipe through its own channel (as the engineering bench does).
 */
public class AutoCraftingBlockEntity extends WorksiteBlockEntity implements io.github.capttracker.ancientwarfare.api.IWorksiteLogistics {
    public final CraftingRecipeMemory craftingRecipeMemory = new CraftingRecipeMemory(this::setChanged);
    public final ItemStackHandler outputInventory = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    public final ItemStackHandler resourceInventory = new ItemStackHandler(18) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private boolean canHoldLastCheck = false;

    /** Only the products are hauled; what the recipes need is up to the player. */
    @Override
    public java.util.List<Output> getLogisticsOutputs() {
        return java.util.List.of(new Output(outputInventory, stack -> true));
    }

    // the recipe is looked up by name; the lookup is kept until the name or the recipe manager (a reload) changes
    private ICraftingRecipe cachedRecipe = NoRecipeWrapper.INSTANCE;
    private ItemStack cachedOutput = ItemStack.EMPTY;
    @Nullable
    private RecipeResourceLocation cachedRecipeId = null;
    @Nullable
    private Object cachedRecipeManager = null;

    public AutoCraftingBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.AUTO_CRAFTING_BE.get(), pos, state);
    }

    /** The registrations of the item handler; called from {@code WorksiteEvents} (mod bus). */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AwAutomationBase.AUTO_CRAFTING_BE.get(),
                (crafting, side) -> crafting.getItemHandler(side));
    }

    /** The inventory a side reaches (null: none). */
    @Nullable
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == null) {
            return null;
        }
        return side == Direction.DOWN ? outputInventory : resourceInventory;
    }

    @Override
    public void onBlockBroken(BlockState state) {
        if (level != null) {
            craftingRecipeMemory.dropInventory(level, worldPosition);
            InventoryTools.dropItemsInWorld(level, outputInventory, worldPosition);
            InventoryTools.dropItemsInWorld(level, resourceInventory, worldPosition);
        }
        super.onBlockBroken(state);
    }

    /** The chosen recipe, or the "no recipe" object. */
    private ICraftingRecipe getRecipe() {
        Level currentLevel = this.level;
        if (currentLevel == null) {
            return NoRecipeWrapper.INSTANCE;
        }
        RecipeResourceLocation id = craftingRecipeMemory.getRecipeId();
        Object manager = currentLevel.getRecipeManager();
        if (cachedRecipeManager != manager || !id.equals(cachedRecipeId)) {
            cachedRecipe = craftingRecipeMemory.getRecipe(currentLevel);
            cachedOutput = cachedRecipe.getRecipeOutput(currentLevel.registryAccess());
            cachedRecipeId = id;
            cachedRecipeManager = manager;
        }
        return cachedRecipe;
    }

    /**
     * Crafts once: finds the ingredients of the recipe in the resource inventory (preferring the exact items of the
     * template), uses them up and puts the result and what is left over into the inventories.
     *
     * @return false if the output is full, there is no recipe or the ingredients are not there
     */
    public boolean tryCraftItem() {
        ICraftingRecipe recipe = getRecipe();
        if (level == null || !recipe.isValid() || !canHold()) {
            return false;
        }
        CraftingInput template = craftingRecipeMemory.getCraftingInput();
        NonNullList<ItemStack> reusableStacks = AWCraftingManager.getReusableStacks(recipe, template);
        IItemHandler pool = new CombinedInvWrapper(resourceInventory, new ItemStackHandler(reusableStacks));
        AWCraftingManager.IngredientMatch match = AWCraftingManager.matchIngredients(recipe, craftingRecipeMemory.getCraftingStacks(), pool);
        if (!match.isSuccess()) {
            return false;
        }
        craftItem(recipe, match, reusableStacks);
        return true;
    }

    private void craftItem(ICraftingRecipe recipe, AWCraftingManager.IngredientMatch match, NonNullList<ItemStack> reusableStacks) {
        // what is really used up: the reusable stacks only exist in the pooled match
        for (ItemStack portion : match.allPortions()) {
            if (reusableStacks.stream().noneMatch(r -> InventoryTools.doItemStacksMatch(portion, r))) {
                AWCraftingManager.extractExact(resourceInventory, portion, portion.getCount());
            }
        }
        NonNullList<ItemStack> resources = InventoryTools.removeItems(match.representativeStacks(), reusableStacks);

        CraftingInput usedInput = usedInput(recipe, match.representativeStacks());
        ItemStack result = recipe.getCraftingResult(usedInput, level.registryAccess());
        NonNullList<ItemStack> remainingItems = InventoryTools.removeItems(recipe.getRemainingItems(usedInput), reusableStacks);

        for (ItemStack stack : remainingItems) {
            if (stack.isEmpty()) {
                continue;
            }

            boolean wasIngredient = resources.stream().anyMatch(used -> ItemStack.isSameItemSameComponents(stack, used) && used.getCount() >= stack.getCount());
            if (wasIngredient) {
                InventoryTools.insertOrDropItem(resourceInventory, stack, level, worldPosition);
            } else {
                InventoryTools.insertOrDropItem(outputInventory, stack, level, worldPosition);
            }
        }

        InventoryTools.insertOrDropItem(outputInventory, result, level, worldPosition);
    }

    /**
     * The grid of the items that were really matched, in the layout of the recipe (the ingredient list is row by row,
     * {@code getRecipeWidth()} columns), so the result and the remaining items are worked out for the real items.
     */
    private static CraftingInput usedInput(ICraftingRecipe recipe, NonNullList<ItemStack> representatives) {
        int width = Math.max(1, recipe.getRecipeWidth());
        int height = Math.max(1, recipe.getRecipeHeight());
        List<ItemStack> grid = new ArrayList<>(width * height);
        for (int i = 0; i < width * height; i++) {
            grid.add(i < representatives.size() ? representatives.get(i) : ItemStack.EMPTY);
        }
        return CraftingInput.of(width, height, grid);
    }

    @Override
    public WorkType getWorkType() {
        return WorkType.CRAFTING;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        craftingRecipeMemory.load(tag, registries);
        resourceInventory.deserializeNBT(registries, tag.getCompound("resourceInventory"));
        outputInventory.deserializeNBT(registries, tag.getCompound("outputInventory"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        craftingRecipeMemory.save(tag, registries);
        tag.put("resourceInventory", resourceInventory.serializeNBT(registries));
        tag.put("outputInventory", outputInventory.serializeNBT(registries));
    }

    /* ***********************************INVENTORY METHODS*********************************************** */
    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide) {
            WorksiteMenus.open(player, WorksiteMenus.Kind.AUTO_CRAFTING, this);
        }
        return true;
    }

    private static final IWorksiteAction CRAFT_ACTION = WorksiteActions.full();

    @Override
    protected Optional<IWorksiteAction> getNextAction() {
        return canHoldLastCheck && !cachedOutput.isEmpty() ? Optional.of(CRAFT_ACTION) : Optional.empty();
    }

    @Override
    protected boolean processAction(IWorksiteAction action) {
        return tryCraftItem();
    }

    @Override
    protected void updateWorksite() {
        canHoldLastCheck = canHold();
    }

    private boolean canHold() {
        getRecipe(); // refreshes the cached output
        return !cachedOutput.isEmpty() && InventoryTools.canInventoryHold(outputInventory, cachedOutput);
    }
}
