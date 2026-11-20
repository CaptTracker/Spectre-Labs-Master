/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/crafting/ResearchCraftingElement (the Core port draws the same thing inside
 * its engineering bench screen; the two Automation crafting screens use this element).
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Composite;
import io.github.capttracker.ancientwarfare.client.gui.elements.GuiElement;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.TexturedButton;
import io.github.capttracker.ancientwarfare.crafting.CraftingMemoryController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * The recipe part of a crafting screen: a research book icon over the book slot and two arrows that step through the
 * recipes the template completes (shown only when there is more than one). The slots themselves are the menu's.
 */
public class ResearchCraftingElement extends Composite {
    private static final int ELEMENT_WIDTH = 176;
    private static final int ELEMENT_HEIGHT = 64;

    private final CraftingMemoryController controller;

    private final TexturedButton previous;
    private final TexturedButton next;

    public ResearchCraftingElement(GuiContainerBase<?> gui, CraftingMemoryController controller, int topLeftX, int topLeftY) {
        super(gui, topLeftX, topLeftY, ELEMENT_WIDTH, ELEMENT_HEIGHT);
        this.controller = controller;

        ItemSlot bookSlotIcon = new ItemSlot(8, 8, new ItemStack(AncientWarfareCore.RESEARCH_BOOK.get()), gui);
        bookSlotIcon.setRenderTooltip(false).setHighlightOnMouseOver(false).setRenderSlotBackground(false).setRenderItemQuantity(false);
        addGuiElement(bookSlotIcon);

        previous = new TexturedButton(122, 26, TexturedButton.TextureSet.LEFT_ARROW) {
            @Override
            protected void onPressed() {
                controller.previousRecipe();
            }
        };
        addGuiElement(previous);

        next = new TexturedButton(152, 26, TexturedButton.TextureSet.RIGHT_ARROW) {
            @Override
            protected void onPressed() {
                controller.nextRecipe();
            }
        };
        addGuiElement(next);
    }

    /** No background and no clipping, only the elements (as the original). */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean visible = controller.getRecipeCount() > 1;
        previous.setVisible(visible);
        next.setVisible(visible);
        for (GuiElement element : this.elements) {
            element.render(graphics, mouseX, mouseY, partialTick);
        }
    }
}
