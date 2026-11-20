/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/ImageButton.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.Listener;
import io.github.capttracker.ancientwarfare.client.gui.TextureData;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;

/** Button drawn from a {@link TextureData} region, tinted with one colour normally and another on hover. */
public abstract class ImageButton extends GuiElement {
    private final TextureData textureData;
    private final Color colorOutOfFocus;
    private final Color colorInFocus;
    private boolean pressed = false;

    public ImageButton(int topLeftX, int topLeftY, int width, int height, TextureData textureData, Color colorOutOfFocus, Color colorInFocus) {
        super(topLeftX, topLeftY, width, height);
        this.textureData = textureData;
        this.colorOutOfFocus = colorOutOfFocus;
        this.colorInFocus = colorInFocus;

        this.addNewListener(new Listener(Listener.MOUSE_UP) {
            @Override
            public boolean onEvent(GuiElement widget, GuiContainerBase.ActivationEvent evt) {
                if (pressed && enabled && visible && isMouseOverElement(evt.mx, evt.my)) {
                    GuiRender.playClickSound();
                    onPressed(evt.mButton);
                }
                pressed = false;
                return true;
            }
        });
        this.addNewListener(new Listener(Listener.MOUSE_DOWN) {
            @Override
            public boolean onEvent(GuiElement widget, GuiContainerBase.ActivationEvent evt) {
                if (enabled && visible && isMouseOverElement(evt.mx, evt.my)) {
                    pressed = true;
                }
                return true;
            }
        });

    }

    protected abstract void onPressed(int mButton);

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (visible) {
            Color color = isMouseOverElement(mouseX, mouseY) ? colorInFocus : colorOutOfFocus;
            // The original handed 0-255 values to a 0-1 colour call, which clamped every non-zero channel to full.
            // The tint is applied as 0-1 here, which is what the callers' Color.GRAY / Color.CYAN meant.
            GuiRender.tint(graphics, color);
            GuiRender.quartered(graphics, textureData.getTexture(), textureData.getTextureWidth(), textureData.getTextureHeight(),
                    textureData.getTextureU(), textureData.getTextureV(), textureData.getPartWidth(), textureData.getPartHeight(),
                    renderX, renderY, width, height);
            GuiRender.resetColor(graphics);
        }
    }
}
