/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/manual/Page.
 */
package io.github.capttracker.ancientwarfare.client.screen.manual;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.TextureData;
import io.github.capttracker.ancientwarfare.client.gui.elements.Composite;
import io.github.capttracker.ancientwarfare.client.gui.elements.GuiElement;
import io.github.capttracker.ancientwarfare.client.screen.manual.elements.BaseElementWrapper;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

import static io.github.capttracker.ancientwarfare.client.screen.manual.GuiManual.FOOTER_HEIGHT;

/** One page of the open book: its content wrappers and the footer with the page number and the turn button. */
public class Page extends Composite {
    private static final int PADDING = 7;
    private final boolean leftPage;
    private final TextureData textureData;

    public Page(GuiContainerBase<?> gui, int topLeftX, int topLeftY, int width, int height, TextureData textureData, boolean leftPage) {
        super(gui, topLeftX, topLeftY, width, height);
        this.textureData = textureData;
        this.leftPage = leftPage;
    }

    public static int getPadding() {
        return PADDING;
    }

    public void updateContentElements(List<BaseElementWrapper<?>> contentElements) {
        clearElements();
        contentElements.forEach(this::addGuiElement);
        addPaging();
    }

    @Override
    public GuiManual getGui() {
        return (GuiManual) super.getGui();
    }

    @Override
    public void updateGuiPosition(int guiLeft, int guiTop) {
        super.updateGuiPosition(guiLeft + PADDING, guiTop + PADDING);
    }

    private void addPaging() {
        addGuiElement(new Paging(getGui(), 0, height - 2 * PADDING - FOOTER_HEIGHT, width - 2 * PADDING, FOOTER_HEIGHT,
                textureData.getTexture(), leftPage));
    }

    /** The page is drawn on the book texture, so no panel and no clipping. */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (GuiElement element : this.elements) {
            element.render(graphics, mouseX, mouseY, partialTick);
        }
    }
}
