/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/GuiResearchBook.
 */
package io.github.capttracker.ancientwarfare.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Checkbox;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.ITooltipRenderer;
import io.github.capttracker.ancientwarfare.crafting.ResearchRecipe;
import io.github.capttracker.ancientwarfare.menu.ResearchBookMenu;
import io.github.capttracker.ancientwarfare.research.ResearchGoal;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The research book: a list of every research goal and, for the selected one, the items it unlocks and the goals it
 * needs; or (item view, the checkbox) a list of every researched item with its recipe and the goal it needs. Shows the
 * recipe only if the local player knows that goal.
 *
 * <p>Shown by the research book item (a menu opened by the server) or over the research bench screen
 * ({@code parent}), which it returns to when closed.
 */
public class GuiResearchBook extends GuiContainerBase<ResearchBookMenu> {
    @Nullable
    private final GuiContainerBase<?> parent;

    private ResearchRecipe selectedRecipe = null;
    private ResourceLocation selectedGoal = null;

    private CompositeScrolled area;
    private CompositeScrolled detailsArea;

    private boolean researchMode = true;

    private Checkbox modeBox;

    public GuiResearchBook(ResearchBookMenu menu) {
        this(menu, null);
    }

    public GuiResearchBook(ResearchBookMenu menu, @Nullable GuiContainerBase<?> parent) {
        super(menu, 400, 240);
        this.parent = parent;
    }

    @Override
    public void initElements() {
        area = new CompositeScrolled(this, 0, 40, xSize / 2, ySize - 40);
        addGuiElement(area);
        modeBox = new Checkbox(8, 8, 16, 16, "guistrings.research.research_mode") {
            @Override
            public void onToggled() {
                researchMode = checked();
                refreshGui();
            }
        };
        modeBox.setChecked(researchMode);
        addGuiElement(modeBox);

        detailsArea = new CompositeScrolled(this, xSize / 2, 40, xSize / 2, ySize - 40);
        addGuiElement(detailsArea);
    }

    @Override
    public void setupElements() {
        area.clearElements();
        detailsArea.clearElements();
        modeBox.setChecked(researchMode);
        if (researchMode) {
            addResearchModeControls();
        } else {
            addRecipeModeControls();
        }
    }

    /** Over the research bench screen the close key goes back to it; the menu stays open. */
    @Override
    protected boolean onGuiCloseRequested() {
        if (parent != null) {
            parent.bindMenu();
            showScreen(parent);
            return false;
        }
        return true;
    }

    private void addRecipeModeControls() {
        int totalHeight = 8;

        for (ResearchRecipe recipe : ResearchScreenData.recipesSorted()) {
            area.addGuiElement(new RecipeButton(8, totalHeight, recipe));
            totalHeight += 12;
        }
        area.setAreaSize(totalHeight);

        totalHeight = 8;
        detailsArea.addGuiElement(new RecipeButton(8, totalHeight, selectedRecipe));
        totalHeight += 14;

        if (selectedRecipe != null) {
            ResourceLocation depResearch = selectedRecipe.research();
            boolean canShow = ResearchScreenData.localPlayerKnows(depResearch);
            int recipeWidth = selectedRecipe.width();
            int recipeHeight = selectedRecipe.height();

            if (canShow) {
                NonNullList<ItemStack> ingredients = NonNullList.withSize(recipeWidth * recipeHeight, ItemStack.EMPTY);
                List<SizedIngredient> slots = selectedRecipe.slots();
                for (int i = 0; i < slots.size() && i < ingredients.size(); i++) {
                    ingredients.set(i, ResearchScreenData.firstStack(slots.get(i)));
                }
                for (int i = 0; i < recipeWidth; i++) {
                    for (int j = 0; j < recipeHeight; j++) {
                        detailsArea.addGuiElement(new ItemSlot(9 + 18 * i, totalHeight + 18 * j, ingredients.get(i + j * recipeWidth), this));
                    }
                }
                totalHeight += (recipeHeight - 1) * 9;
                detailsArea.addGuiElement(new Label(8 + 18 * (recipeWidth + 1), totalHeight + 2, "->"));
            }
            detailsArea.addGuiElement(new ItemSlot(9 + 18 * (recipeWidth + 2), totalHeight, selectedRecipe.result(), this));
            if (canShow) {
                totalHeight += (recipeHeight + 1) * 9;
            } else {
                totalHeight += 20;
            }
            detailsArea.addGuiElement(new Label(8, totalHeight, "guistrings.research.research_needed"));
            totalHeight += 14;
            if (ResearchScreenData.goal(depResearch) != null) {
                detailsArea.addGuiElement(new GoalButton(8, totalHeight, depResearch));
            }
        }
    }

    private void addResearchModeControls() {
        int totalHeight = 8;
        for (ResourceLocation goal : ResearchScreenData.allGoalsSorted()) {
            area.addGuiElement(new GoalButton(8, totalHeight, goal));
            totalHeight += 12;
        }
        area.setAreaSize(totalHeight);

        totalHeight = 8;
        detailsArea.addGuiElement(new GoalButton(8, totalHeight, selectedGoal));
        totalHeight += 16;

        if (selectedGoal != null) {
            List<ResearchRecipe> list = new ArrayList<>();
            for (ResearchRecipe recipe : ResearchScreenData.recipesSorted()) {
                if (recipe.research().equals(selectedGoal)) {
                    list.add(recipe);
                }
            }
            if (!list.isEmpty()) {
                detailsArea.addGuiElement(new Label(8, totalHeight, "guistrings.research.researched_items"));
                totalHeight += 14;
                for (int i = 0; i < list.size(); i++) {
                    detailsArea.addGuiElement(new RecipeSlot(i, totalHeight, list.get(i), this));
                }
                totalHeight += 18 * (1 + list.size() / 9);
            }
            ResearchGoal goal = ResearchScreenData.goal(selectedGoal);
            if (goal != null && !goal.dependencies().isEmpty()) {
                detailsArea.addGuiElement(new Label(8, totalHeight, "guistrings.research.research_needed"));
                totalHeight += 14;
                for (ResourceLocation dependency : goal.dependencies()) {
                    detailsArea.addGuiElement(new GoalButton(8, totalHeight, dependency));
                    totalHeight += 12;
                }
            }
        }
    }

    /** An item of the researched-items list; clicking it switches to the item view with that recipe selected. */
    private class RecipeSlot extends ItemSlot {
        private final ResearchRecipe researched;

        RecipeSlot(int i, int totalHeight, ResearchRecipe recipe, ITooltipRenderer render) {
            super(8 + (18 * (i % 9)), totalHeight + 18 * (i / 9), recipe.result(), render);
            setRenderItemQuantity(false);
            researched = recipe;
        }

        @Override
        public void onSlotClicked(ItemStack stack, boolean rightClicked) {
            selectedRecipe = researched;
            researchMode = false;
            refreshGui();
        }
    }

    private class RecipeButton extends Button {
        private final ResearchRecipe recipe;

        RecipeButton(int topLeftX, int topLeftY, @Nullable ResearchRecipe recipe) {
            super(topLeftX, topLeftY, 160, 12, recipe == null ? "guistrings.no_selection" : recipe.result().getHoverName().getString());
            this.recipe = recipe;
        }

        @Override
        protected void onPressed() {
            selectedRecipe = recipe;
            refreshGui();
        }
    }

    private class GoalButton extends Button {
        private final ResourceLocation goal;

        GoalButton(int topLeftX, int topLeftY, @Nullable ResourceLocation goal) {
            super(topLeftX, topLeftY, 160, 10, goal == null ? "guistrings.no_selection" : ResearchScreenData.name(goal));
            this.goal = goal;
        }

        @Override
        protected void onPressed() {
            selectedGoal = goal;
            researchMode = true;
            refreshGui();
        }
    }
}
