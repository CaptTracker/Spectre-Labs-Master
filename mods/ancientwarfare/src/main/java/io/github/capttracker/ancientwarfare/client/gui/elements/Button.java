/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/Button.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase.ActivationEvent;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.Listener;
import net.minecraft.client.gui.GuiGraphics;

/** Texture-backed text button. The click fires on mouse-up inside the button. */
public class Button extends GuiElement {

    private boolean pressed = false;
    protected String text;
    protected int textX;
    protected int textY;

    public Button(int topLeftX, int topLeftY, int width, int height, String text) {
        super(topLeftX, topLeftY, width, height);
        this.setText(text);
        this.setTooltipIfFound(text);
        this.addNewListener(new Listener(Listener.MOUSE_UP) {
            @Override
            public boolean onEvent(GuiElement widget, ActivationEvent evt) {
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
            public boolean onEvent(GuiElement widget, ActivationEvent evt) {
                if (enabled && visible && isMouseOverElement(evt.mx, evt.my)) {
                    pressed = true;
                }
                return true;
            }
        });
    }

    public final void setText(String text) {
        this.text = translate(text);
        int tw = font().width(this.text);
        if (tw > width) {
            width = tw;
        }
        textX = (width - tw) / 2;
        textY = (height - 8) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (visible) {
            int startX = 0;
            int startY = enabled ? isMouseOverElement(mouseX, mouseY) ? 80 : 40 : 0;
            // grey on hover, white otherwise. (The original value 0xa0a0a0ff was RGBA written where ARGB is read.)
            int textColor = startY == 80 ? 0xFFA0A0A0 : 0xFFFFFFFF;
            GuiRender.quartered(graphics, widgetTexture1, 256, 256, startX, startY, 256, 40, renderX, renderY, width, height);
            graphics.drawString(font(), text, renderX + textX, renderY + textY, textColor, true);
        }
    }

    /*
     * sub-classes may override this as an on-pressed callback
     * method is called whenever the 'pressed' sound is played.
     * uses built-in click listener for sound to trigger method
     */
    protected void onPressed() {

    }

    /*
     * Button-sensitive version of onPressed. 0 = LMB, 1 = RMB
     * @param mButton
     */
    protected void onPressed(int mButton) {
        onPressed(); // backwards compatibility
    }

}
