/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/ClickableArea.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase.ActivationEvent;
import io.github.capttracker.ancientwarfare.client.gui.Listener;
import net.minecraft.client.gui.GuiGraphics;

/*
 * simple clickable area interface widget...may be stacked on top of other buttons/labels
 * in order to easier facilitate a common on-clicked functionality for an entire group/area
 * of widgets.
 *
 * @author Shadowmage
 */
public abstract class ClickableArea extends GuiElement {

    public ClickableArea(int topLeftX, int topLeftY, int width, int height) {
        super(topLeftX, topLeftY, width, height);
        Listener listener = new Listener(Listener.MOUSE_DOWN) {
            @Override
            public boolean onEvent(GuiElement widget, ActivationEvent evt) {
                if (ClickableArea.this.isMouseOverElement(evt.mx, evt.my)) {
                    onClicked();
                }
                return true;
            }
        };
        addNewListener(listener);
    }

    protected abstract void onClicked();

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //NOOP
    }

}
