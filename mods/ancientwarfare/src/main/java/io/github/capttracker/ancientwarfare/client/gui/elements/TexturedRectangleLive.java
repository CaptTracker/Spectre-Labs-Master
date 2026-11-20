/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/TexturedRectangleLive.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class TexturedRectangleLive extends GuiElement {

    ResourceLocation tex;
    int tx, ty, u, v, uw, vh;

    public TexturedRectangleLive(int topLeftX, int topLeftY, int width, int height, int tx, int ty, int u, int v, int uw, int vh, ResourceLocation tex) {
        super(topLeftX, topLeftY, width, height);
        this.tx = tx;//texture X size
        this.ty = ty;//texture Y size
        this.u = u;//start position in texture X axis
        this.v = v;//start position in texture Y axis
        this.uw = uw;//width of used texture
        this.vh = vh;//height of used texture
        this.tex = tex;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (visible) {
            GuiRender.region(graphics, tex, tx, ty, u, v, uw, vh, renderX, renderY, width, height);
        }
    }

}
