/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/TexturedRectangle.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** A texture region stretched over the widget's rectangle. */
public class TexturedRectangle extends GuiElement {

    ResourceLocation texture;
    private final int tx, ty, u, v, uw, vh;

    /**
     * @param tx texture width in pixels
     * @param ty texture height in pixels
     * @param u  region start U
     * @param v  region start V
     * @param uw region width
     * @param vh region height
     */
    public TexturedRectangle(int topLeftX, int topLeftY, int width, int height, ResourceLocation texture, int tx, int ty, int u, int v, int uw, int vh) {
        super(topLeftX, topLeftY, width, height);
        this.tx = tx;
        this.ty = ty;
        this.u = u;
        this.v = v;
        this.uw = uw;
        this.vh = vh;
        this.texture = texture;
    }

    public void setTexture(ResourceLocation texture) {
        this.texture = texture;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (visible && texture != null) {
            GuiRender.region(graphics, texture, tx, ty, u, v, uw, vh, renderX, renderY, width, height);
        }
    }

}
