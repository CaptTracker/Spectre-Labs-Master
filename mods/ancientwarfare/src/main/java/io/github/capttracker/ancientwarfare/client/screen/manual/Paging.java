/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/manual/Paging.
 */
package io.github.capttracker.ancientwarfare.client.screen.manual;

import io.github.capttracker.ancientwarfare.client.gui.TextureData;
import io.github.capttracker.ancientwarfare.client.gui.elements.Composite;
import io.github.capttracker.ancientwarfare.client.gui.elements.GuiElement;
import io.github.capttracker.ancientwarfare.client.gui.elements.ImageButton;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.awt.Color;

/** The footer of a page: the page number and the button that turns to the previous (left page) or next (right page) pair of pages. */
public class Paging extends Composite {
    private static final int TOP_PADDING = 4;
    private static final int SIDE_PADDING = 7;
    private final boolean leftPage;
    private final PagingButton pagingButton;

    public Paging(GuiManual gui, int topLeftX, int topLeftY, int width, int height, ResourceLocation manualTexture, boolean leftPage) {
        super(gui, topLeftX, topLeftY, width, height);
        this.leftPage = leftPage;

        if (leftPage) {
            pagingButton = new PagingButton(SIDE_PADDING, PagingButton.BUTTON_HEIGHT, manualTexture) {
                @Override
                protected void onPressed(int mButton) {
                    gui.setCurrentPageIndex(Math.max(gui.getCurrentPageIndex() - 2, 0));
                    updateButtonVisibility();
                }
            };
        } else {
            pagingButton = new PagingButton(width - PagingButton.BUTTON_WIDTH - SIDE_PADDING, 0, manualTexture) {
                @Override
                protected void onPressed(int mButton) {
                    gui.setCurrentPageIndex(Math.min(gui.getCurrentPageIndex() + 2, gui.getPageCount() - 1));
                    updateButtonVisibility();
                }
            };
        }
        addGuiElement(pagingButton);
        addGuiElement(new Label(width / 2, TOP_PADDING, Integer.toString(gui.getCurrentPageIndex() + (leftPage ? 1 : 2)))
                .setColor(Color.GRAY.getRGB()).setRenderCentered());
        updateButtonVisibility();
    }

    @Override
    public GuiManual getGui() {
        return (GuiManual) super.getGui();
    }

    private void updateButtonVisibility() {
        pagingButton.setVisible((leftPage && getGui().getCurrentPageIndex() > 0) || (!leftPage && getGui().getCurrentPageIndex() + 2 < getGui().getPageCount()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (GuiElement element : this.elements) {
            element.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private abstract static class PagingButton extends ImageButton {
        static final int BUTTON_WIDTH = 18;
        static final int BUTTON_HEIGHT = 10;

        private PagingButton(int topLeftX, int textureV, ResourceLocation manualTexture) {
            super(topLeftX, TOP_PADDING, BUTTON_WIDTH, BUTTON_HEIGHT,
                    new TextureData(manualTexture, 512, 512, 412, textureV, BUTTON_WIDTH, BUTTON_HEIGHT), Color.GRAY, Color.CYAN);
        }
    }
}
