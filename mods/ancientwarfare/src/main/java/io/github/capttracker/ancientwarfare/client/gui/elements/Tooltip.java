/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/Tooltip.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Not a widget: a small tree of widgets (usually Labels) drawn in a vanilla-styled box near the
 * mouse. The screen draws it last.
 */
public class Tooltip {

    private final List<GuiElement> children = new ArrayList<>();
    int width, height;

    /*
     * actual size of the tooltip is width+8, height+8 (it draws some borders around the internals)
     */
    public Tooltip(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The original compared against the framebuffer size in pixels; the mouse is in GUI units.
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        if (mouseX + 12 + this.width + 4 > screenWidth) {
            mouseX -= 28 + this.width;
        }
        if (mouseY + this.height + 6 > screenHeight) {
            mouseY = screenHeight - this.height - 6;
        }

        // tooltip needs a right-offset from cursor
        mouseX += 12;

        for (GuiElement element : this.children) {
            element.updateGuiPosition(mouseX, mouseY);
        }

        graphics.pose().pushPose();
        // above items (150) and their counts (200) and the carried item (232), like vanilla tooltips
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        graphics.flush();
        graphics.enableScissor(mouseX - 4, mouseY - 4, mouseX + this.width + 4, mouseY + this.height + 4);
        drawBackground(graphics, mouseX, mouseY);

        for (GuiElement element : this.children) {
            element.render(graphics, -1000, -1000, partialTick);
        }
        graphics.disableScissor();
        graphics.pose().popPose();
    }

    public final void addTooltipElement(GuiElement element) {
        if (this.width < element.width) {
            this.width = element.width + 2;
        }
        if (this.height < element.height) {
            this.height = element.height + 2;
        }
        this.children.add(element);
    }

    private void drawBackground(GuiGraphics g, int x, int y) {
        int color1 = 0xF0100010;
        g.fillGradient(x - 3, y - 4, x + this.width + 3, y - 3, color1, color1);
        g.fillGradient(x - 3, y + this.height + 3, x + this.width + 3, y + this.height + 4, color1, color1);
        g.fillGradient(x - 3, y - 3, x + this.width + 3, y + this.height + 3, color1, color1);
        g.fillGradient(x - 4, y - 3, x - 3, y + this.height + 3, color1, color1);
        g.fillGradient(x + this.width + 3, y - 3, x + this.width + 4, y + this.height + 3, color1, color1);
        int color2 = 0x505000FF;
        int color3 = (color2 & 0xFEFEFE) >> 1 | color2 & 0xFF000000;
        g.fillGradient(x - 3, y - 3 + 1, x - 3 + 1, y + this.height + 3 - 1, color2, color3);
        g.fillGradient(x + this.width + 2, y - 3 + 1, x + this.width + 3, y + this.height + 3 - 1, color2, color3);
        g.fillGradient(x - 3, y - 3, x + this.width + 3, y - 3 + 1, color2, color2);
        g.fillGradient(x - 3, y + this.height + 2, x + this.width + 3, y + this.height + 3, color3, color3);
    }

}
