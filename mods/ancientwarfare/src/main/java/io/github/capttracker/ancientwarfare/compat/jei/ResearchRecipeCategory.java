/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/compat/jei/ResearchRecipeCategory.
 */
package io.github.capttracker.ancientwarfare.compat.jei;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.crafting.ICraftingRecipe;
import io.github.capttracker.ancientwarfare.crafting.ResearchRecipe;
import io.github.capttracker.ancientwarfare.research.ResearchGoal;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.Arrays;
import java.util.List;

/** The engineering bench's recipes (a 3x3 grid, counted ingredients, and the research needed) shown in JEI. */
public class ResearchRecipeCategory implements IRecipeCategory<ResearchRecipe> {
    public static final RecipeType<ResearchRecipe> TYPE = RecipeType.create(AncientWarfareCore.MOD_ID, "research", ResearchRecipe.class);

    private static final int WIDTH = 116;
    private static final int HEIGHT = 66;

    private final IDrawable icon;

    public ResearchRecipeCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(AncientWarfareCore.ENGINEERING_STATION.get()));
    }

    @Override
    public RecipeType<ResearchRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ancientwarfare.category.research");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ResearchRecipe recipe, IFocusGroup focuses) {
        List<SizedIngredient> slots = recipe.slots();
        for (int i = 0; i < slots.size(); i++) {
            SizedIngredient slot = slots.get(i);
            if (ICraftingRecipe.isEmptySlot(slot)) {
                continue;
            }
            int x = i % recipe.width();
            int y = i / recipe.width();
            builder.addSlot(RecipeIngredientRole.INPUT, x * 18 + 1, y * 18 + 1)
                    .addItemStacks(Arrays.asList(slot.getItems()));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 95, 19).addItemStack(recipe.result());
    }

    @Override
    public void draw(ResearchRecipe recipe, IRecipeSlotsView slotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        Component text = Component.translatable("jei.ancientwarfare.research", ResearchGoal.displayName(recipe.research()));
        graphics.drawString(Minecraft.getInstance().font, text, 0, 56, 0xFF808080, false);
    }
}
