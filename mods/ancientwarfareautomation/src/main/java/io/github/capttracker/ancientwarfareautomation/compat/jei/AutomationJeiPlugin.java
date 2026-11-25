/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the automation part of core/compat/jei/AWJEIPlugin. Only JEI loads this class.
 */
package io.github.capttracker.ancientwarfareautomation.compat.jei;

import io.github.capttracker.ancientwarfare.compat.jei.AwRecipeTransferHandler;
import io.github.capttracker.ancientwarfare.compat.jei.ResearchRecipeCategory;
import io.github.capttracker.ancientwarfareautomation.AncientWarfareAutomation;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import io.github.capttracker.ancientwarfareautomation.menu.AutoCraftingMenu;
import io.github.capttracker.ancientwarfareautomation.menu.WarehouseCraftingStationMenu;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** The "+" button for the auto crafting block and the warehouse crafting station, and their catalyst entries. */
@JeiPlugin
public class AutomationJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(AncientWarfareAutomation.MOD_ID, "jei");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        AwRecipeTransferHandler.register(registration, AutoCraftingMenu.class, AwAutomationMenus.AUTO_CRAFTING.get());
        AwRecipeTransferHandler.register(registration, WarehouseCraftingStationMenu.class, AwAutomationMenus.WAREHOUSE_CRAFTING_STATION.get());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (var block : new net.minecraft.world.level.ItemLike[]{AwAutomationBase.AUTO_CRAFTING.get(), AwAutomationBase.WAREHOUSE_CRAFTING.get()}) {
            registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, new ItemStack(block), ResearchRecipeCategory.TYPE);
            registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, new ItemStack(block), RecipeTypes.CRAFTING);
        }
    }
}
