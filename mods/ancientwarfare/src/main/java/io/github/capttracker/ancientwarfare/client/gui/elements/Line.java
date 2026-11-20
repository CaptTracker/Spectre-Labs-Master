/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/Line.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A straight line. The original drew a GL line; this draws the same segment as a row of filled
 * squares of {@code lineWidth} GUI pixels (exact for horizontal and vertical lines).
 */
public class Line extends GuiElement {

    int lineWidth;
    int color;
    int x2, y2;

    /*
     * @param color RGBA color (0xRRGGBBAA)
     */
    public Line(int topLeftX, int topLeftY, int x2, int y2, int lineWidth, int color) {
        super(topLeftX, topLeftY, x2 - topLeftX, y2 - topLeftY);
        this.color = color;
        this.x2 = x2 - topLeftX;
        this.y2 = y2 - topLeftY;
        this.lineWidth = lineWidth;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int argb = GuiRender.rgbaToArgb(color);
        int w = Math.max(1, lineWidth);
        int steps = Math.max(Math.abs(x2), Math.abs(y2));
        if (steps == 0) {
            graphics.fill(renderX, renderY, renderX + w, renderY + w, argb);
            return;
        }
        if (y2 == 0) {
            graphics.fill(Math.min(renderX, renderX + x2), renderY, Math.max(renderX, renderX + x2), renderY + w, argb);
            return;
        }
        if (x2 == 0) {
            graphics.fill(renderX, Math.min(renderY, renderY + y2), renderX + w, Math.max(renderY, renderY + y2), argb);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int px = renderX + Math.round((float) x2 * i / steps);
            int py = renderY + Math.round((float) y2 * i / steps);
            graphics.fill(px, py, px + w, py + w, argb);
        }
    }

}
