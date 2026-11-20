/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/Rectangle.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import net.minecraft.client.gui.GuiGraphics;

/** Filled rectangle. Colours are 0xRRGGBBAA, as in the original. */
public class Rectangle extends GuiElement {

    private final int color, hoverColor;

    public Rectangle(int topLeftX, int topLeftY, int width, int height, int color, int hoverColor) {
        super(topLeftX, topLeftY, width, height);
        this.color = color;
        this.hoverColor = hoverColor;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiRender.fillRgba(graphics, renderX, renderY, renderX + width, renderY + height, getColor(mouseX, mouseY));
    }

    protected int getColor(int mouseX, int mouseY) {
        return isMouseOverElement(mouseX, mouseY) ? hoverColor : color;
    }

}
