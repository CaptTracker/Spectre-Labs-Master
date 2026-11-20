/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/crafting/GuiEngineeringStation and ResearchCraftingElement.
 */
package io.github.capttracker.ancientwarfare.client;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.TexturedButton;
import io.github.capttracker.ancientwarfare.menu.EngineeringStationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * The engineering bench screen: the slots are the menu's; this adds the book icon above the book slot and the two
 * arrows that step through the recipes the grid completes (shown only when there is more than one). The recipe
 * list is read from the menu, which receives it from the server.
 *
 * <p>Register with {@code event.register(ENGINEERING_STATION_MENU.get(), (menu, inventory, title) -> new EngineeringStationScreen(menu))}.
 */
public class EngineeringStationScreen extends GuiContainerBase<EngineeringStationMenu> {
    private TexturedButton previous;
    private TexturedButton next;
    private Label position;

    public EngineeringStationScreen(EngineeringStationMenu menu) {
        super(menu, 176, 192);
    }

    @Override
    public void initElements() {
        ItemSlot bookSlotIcon = new ItemSlot(8, 8, new ItemStack(AncientWarfareCore.RESEARCH_BOOK.get()), this);
        bookSlotIcon.setRenderTooltip(false).setHighlightOnMouseOver(false).setRenderSlotBackground(false).setRenderItemQuantity(false);
        addGuiElement(bookSlotIcon);

        previous = new TexturedButton(122, 26, TexturedButton.TextureSet.LEFT_ARROW) {
            @Override
            protected void onPressed() {
                getContainer().previousRecipe();
            }
        };
        addGuiElement(previous);

        next = new TexturedButton(152, 26, TexturedButton.TextureSet.RIGHT_ARROW) {
            @Override
            protected void onPressed() {
                getContainer().nextRecipe();
            }
        };
        addGuiElement(next);

        position = new Label(134, 46, "");
        addGuiElement(position);
    }

    @Override
    public void setupElements() {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int count = getContainer().getRecipeCount();
        boolean several = count > 1;
        previous.setVisible(several);
        next.setVisible(several);
        position.setText(several ? (getContainer().getSelectedRecipeIndex() + 1) + "/" + count : "");
        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}
