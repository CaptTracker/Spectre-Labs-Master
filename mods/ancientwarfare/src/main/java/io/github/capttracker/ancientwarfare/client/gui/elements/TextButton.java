/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/TextButton.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.Listener;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;

/** Text-only button drawn in the unicode (uniform) font; the colour changes on hover. */
public abstract class TextButton extends GuiElement {
    private final String text;
    private final Color colorOutOfFocus;
    private final Color colorInFocus;
    private boolean pressed = false;

    protected TextButton(int topLeftX, int topLeftY, int width, int height, String text, Color colorOutOfFocus, Color colorInFocus) {
        super(topLeftX, topLeftY, width, height);
        this.text = text;
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
            graphics.drawString(font(), GuiRender.unicode(text), renderX, renderY, color.getRGB(), false);
        }
    }
}
