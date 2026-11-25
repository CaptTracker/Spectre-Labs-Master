/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/Label.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Static text. Wraps when it has been given a width smaller than the text. */
public class Label extends GuiElement {

    private String text;
    private boolean renderCentered = false;
    private int color = 0xffffffff;
    private boolean renderShadow;

    public Label(int topLeftX, int topLeftY, String text) {
        super(topLeftX, topLeftY);
        setText(text);
    }

    public Label setRenderCentered() {
        this.renderCentered = true;
        return this;
    }

    public Label setColor(int color) {
        this.color = color;
        return this;
    }

    public Label setShadow(boolean renderShadow) {
        this.renderShadow = renderShadow;
        return this;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (visible) {
            if (renderCentered) {
                drawText(graphics, width / 2);
            } else {
                drawText(graphics, 0);
            }
        }
    }

    private void drawText(GuiGraphics graphics, int offset) {
        if (width < font().width(text)) {
            graphics.drawWordWrap(font(), Component.literal(text), renderX - offset, renderY, width, 0xFFDDDDDD);
        } else {
            graphics.drawString(font(), text, renderX - offset, renderY, color, renderShadow);
        }
    }

    public void setText(String text) {
        if (text == null) {
            text = "";
        }
        this.text = translate(text);
        this.setTooltipIfFound(text);
        this.width = font().width(this.text);
        this.height = 8;
    }

    public String getText() {
        return text;
    }

}
