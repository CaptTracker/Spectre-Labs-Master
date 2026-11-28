/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): the cooking pot and the cutting board of Farmer's Delight for the cook. Loaded only when the mod is installed.
 */
package io.github.capttracker.ancientwarfarenpc.compat.farmersdelight;

import io.github.capttracker.ancientwarfare.util.FakePlayers;
import io.github.capttracker.ancientwarfarenpc.compat.KitchenCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Farmer's Delight 1.3 (NeoForge 1.21.1). The pot cooks by itself when its ingredient slots hold the ingredients of a recipe and a heat source is under it
 * (slots 0 to 5 are the ingredients, slot 7 the container, slot 8 the output), so the cook only fills it and empties it. The board is used through its own
 * {@code processStoredItemUsingTool} (with the fake player of the core module, as the farms use it), and what it spawns is picked up from the ground
 * at once. Recipes come from the recipe manager by the recipe types of the mod.
 */
public final class FarmersDelightKitchen implements KitchenCompat {
    private static final int INGREDIENT_SLOTS = CookingPotRecipe.INPUT_SLOTS;

    @Nullable
    @Override
    public Kind kindOf(BlockEntity be) {
        if (be instanceof CookingPotBlockEntity) {
            return Kind.POT;
        }
        return be instanceof CuttingBoardBlockEntity ? Kind.BOARD : null;
    }

    @Override
    public List<Dish> dishes(ServerLevel level) {
        List<Dish> dishes = new ArrayList<>();
        for (RecipeHolder<CookingPotRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.COOKING.get())) {
            CookingPotRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty() || recipe.getIngredients().isEmpty() || recipe.getIngredients().size() > INGREDIENT_SLOTS) {
                continue;
            }
            dishes.add(new Dish(holder.id(), List.copyOf(recipe.getIngredients()), result.copy(), recipe.getOutputContainer().copy()));
        }
        return dishes;
    }

    @Override
    public List<Cut> cuts(ServerLevel level) {
        List<Cut> cuts = new ArrayList<>();
        for (RecipeHolder<CuttingBoardRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CUTTING.get())) {
            CuttingBoardRecipe recipe = holder.value();
            if (recipe.getIngredients().isEmpty() || recipe.getResults().isEmpty()) {
                continue;
            }
            Ingredient input = recipe.getIngredients().get(0);
            cuts.add(new Cut(holder.id(), input, recipe.getTool(), recipe.getResults().stream().map(ItemStack::copy).toList()));
        }
        return cuts;
    }

    @Nullable
    @Override
    public Pot pot(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof CookingPotBlockEntity pot)) {
            return null;
        }
        ItemStackHandler inventory = pot.getInventory();
        boolean busy = !inventory.getStackInSlot(CookingPotBlockEntity.MEAL_DISPLAY_SLOT).isEmpty();
        for (int slot = 0; slot < INGREDIENT_SLOTS; slot++) {
            busy |= !inventory.getStackInSlot(slot).isEmpty();
        }
        return new Pot(pot.isHeated(level, pos), busy, inventory.getStackInSlot(CookingPotBlockEntity.OUTPUT_SLOT).copy());
    }

    @Override
    public boolean fillPot(ServerLevel level, BlockPos pos, List<ItemStack> ingredients, ItemStack container) {
        Pot state = pot(level, pos);
        if (state == null || !state.heated() || state.busy() || ingredients.isEmpty() || ingredients.size() > INGREDIENT_SLOTS
                || !(level.getBlockEntity(pos) instanceof CookingPotBlockEntity pot)) {
            return false;
        }
        ItemStackHandler inventory = pot.getInventory();
        if (!container.isEmpty() && !inventory.getStackInSlot(CookingPotBlockEntity.CONTAINER_SLOT).isEmpty()
                && !ItemStack.isSameItemSameComponents(inventory.getStackInSlot(CookingPotBlockEntity.CONTAINER_SLOT), container)) {
            return false; // somebody left another container in it
        }
        for (int i = 0; i < ingredients.size(); i++) {
            inventory.setStackInSlot(i, ingredients.get(i).copyWithCount(1));
        }
        if (!container.isEmpty() && inventory.getStackInSlot(CookingPotBlockEntity.CONTAINER_SLOT).isEmpty()) {
            inventory.setStackInSlot(CookingPotBlockEntity.CONTAINER_SLOT, container.copyWithCount(1));
        }
        return true;
    }

    @Override
    public ItemStack takePotOutput(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof CookingPotBlockEntity pot)) {
            return ItemStack.EMPTY;
        }
        ItemStackHandler inventory = pot.getInventory();
        ItemStack stack = inventory.getStackInSlot(CookingPotBlockEntity.OUTPUT_SLOT);
        return stack.isEmpty() ? ItemStack.EMPTY : inventory.extractItem(CookingPotBlockEntity.OUTPUT_SLOT, stack.getCount(), false);
    }

    @Override
    public boolean boardFree(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof CuttingBoardBlockEntity board && board.isEmpty();
    }

    @Nullable
    @Override
    public List<ItemStack> cut(ServerLevel level, BlockPos pos, ItemStack input, ItemStack tool) {
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof CuttingBoardBlockEntity board) || !board.isEmpty() || input.isEmpty()) {
            return null;
        }
        ItemStack one = input.copyWithCount(1);
        if (!board.canAddItem(one)) {
            return null;
        }
        board.addItem(one);
        AABB around = new AABB(pos).inflate(2.0D);
        List<ItemEntity> before = level.getEntitiesOfClass(ItemEntity.class, around);
        if (!board.processStoredItemUsingTool(tool, FakePlayers.get(level))) {
            board.removeItem(); // nothing was cut: the item stays with the caller
            return null;
        }
        List<ItemStack> results = new ArrayList<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, around)) {
            if (!before.contains(item) && item.isAlive()) {
                results.add(item.getItem().copy());
                item.discard();
            }
        }
        return results;
    }
}
