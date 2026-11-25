/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the ResearchRegistry and ResearchTracker lookups of core/gui/GuiResearchBook,
 * core/gui/research/GuiResearchStation and GuiResearchStationSelection.
 */
package io.github.capttracker.ancientwarfare.client.screen;

import io.github.capttracker.ancientwarfare.config.AWCoreStatics;
import io.github.capttracker.ancientwarfare.crafting.AWCraftingManager;
import io.github.capttracker.ancientwarfare.crafting.ResearchRecipe;
import io.github.capttracker.ancientwarfare.research.Research;
import io.github.capttracker.ancientwarfare.research.ResearchGoal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What the research screens know on the client: the research goals (a synced data pack registry), the research recipes
 * (synced like every recipe) and the local player's own research (synced by {@code ResearchSyncPayload}).
 */
final class ResearchScreenData {
    private ResearchScreenData() {}

    @Nullable
    static Registry<ResearchGoal> goals() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : level.registryAccess().registry(ResearchGoal.REGISTRY_KEY).orElse(null);
    }

    @Nullable
    static ResearchGoal goal(ResourceLocation id) {
        Registry<ResearchGoal> goals = goals();
        return goals == null ? null : goals.get(id);
    }

    static String name(ResourceLocation id) {
        return ResearchGoal.displayName(id).getString();
    }

    /** All goal ids, sorted by their displayed name (the original's ResearchSorter). */
    static List<ResourceLocation> allGoalsSorted() {
        Registry<ResearchGoal> goals = goals();
        List<ResourceLocation> ret = new ArrayList<>();
        if (goals != null) {
            ret.addAll(goals.keySet());
        }
        ret.sort(Comparator.comparing(ResearchScreenData::name));
        return ret;
    }

    /** The enabled research recipes, sorted by the name of what they make. */
    static List<ResearchRecipe> recipesSorted() {
        ClientLevel level = Minecraft.getInstance().level;
        List<ResearchRecipe> ret = new ArrayList<>();
        if (level != null) {
            for (RecipeHolder<ResearchRecipe> holder : AWCraftingManager.getResearchRecipes(level.getRecipeManager())) {
                if (AWCraftingManager.isCraftable(holder.value())) {
                    ret.add(holder.value());
                }
            }
        }
        ret.sort(Comparator.comparing(r -> r.result().getHoverName().getString()));
        return ret;
    }

    /** 1.12.2: ResearchTracker.hasPlayerCompleted for the local player, which is always true with the research system off. */
    static boolean localPlayerKnows(ResourceLocation goal) {
        return !AWCoreStatics.useResearchSystem || Research.localPlayerKnows(goal);
    }

    /** One stack per resource the goal uses up (1.12.2: ResearchGoal.getResourcesForDisplay): the first item the ingredient accepts. */
    static List<ItemStack> resourcesForDisplay(ResearchGoal goal) {
        List<ItemStack> ret = new ArrayList<>();
        for (SizedIngredient resource : goal.resources()) {
            ret.add(firstStack(resource));
        }
        return ret;
    }

    static ItemStack firstStack(SizedIngredient ingredient) {
        ItemStack[] stacks = ingredient.getItems();
        return stacks.length > 0 ? stacks[0] : ItemStack.EMPTY;
    }
}
