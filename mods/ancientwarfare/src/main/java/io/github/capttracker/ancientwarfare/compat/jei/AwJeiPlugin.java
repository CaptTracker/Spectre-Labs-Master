/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/compat/jei/AWJEIPlugin (recipe display only; no bench recipe transfer yet).
 */
package io.github.capttracker.ancientwarfare.compat.jei;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.crafting.AWCraftingManager;
import io.github.capttracker.ancientwarfare.crafting.ResearchRecipe;
import io.github.capttracker.ancientwarfare.init.AwCoreStations;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/** JEI integration. Only JEI loads this package (it is found by the {@link JeiPlugin} annotation). */
@JeiPlugin
public class AwJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(AncientWarfareCore.MOD_ID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ResearchRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        List<ResearchRecipe> recipes = new ArrayList<>();
        for (RecipeHolder<ResearchRecipe> holder : AWCraftingManager.getResearchRecipes(level.getRecipeManager())) {
            if (AWCraftingManager.isCraftable(holder.value())) {
                recipes.add(holder.value());
            }
        }
        registration.addRecipes(ResearchRecipeCategory.TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, new ItemStack(AncientWarfareCore.ENGINEERING_STATION.get()), ResearchRecipeCategory.TYPE);
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, new ItemStack(AwCoreStations.RESEARCH_STATION.get()), ResearchRecipeCategory.TYPE);
    }
}
