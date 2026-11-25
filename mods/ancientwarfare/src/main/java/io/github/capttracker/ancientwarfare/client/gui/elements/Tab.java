/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/Tab.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase.ActivationEvent;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.ITabCallback;
import io.github.capttracker.ancientwarfare.client.gui.Listener;
import net.minecraft.client.gui.GuiGraphics;

/*
 * tab element for use by CompositeTabbed.  Only has minimal self function
 *
 * @author Shadowmage
 */
public class Tab extends GuiElement {

    ITabCallback parent;
    String label;
    boolean top;

    public Tab(int topLeftX, int topLeftY, boolean top, String label, ITabCallback parentCaller) {
        super(topLeftX, topLeftY);
        this.width = font().width(label) + 6;
        this.label = label;
        this.setTooltipIfFound(label);
        this.height = 14;
        this.parent = parentCaller;
        this.top = top;
        this.addNewListener(new Listener(Listener.MOUSE_UP) {
            @Override
            public boolean onEvent(GuiElement widget, ActivationEvent evt) {
                if (visible && enabled && !selected() && isMouseOverElement(evt.mx, evt.my)) {
                    setSelected(true);
                    GuiRender.playClickSound();
                    if (parent != null) {
                        parent.onTabSelected(Tab.this);
                    }
                }
                return true;
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (visible) {
            int y = 162;
            if (selected) {
                y = 138;
            }
            if (!top) {
                y += 48;
            }
            GuiRender.quartered(graphics, widgetTexture1, 256, 256, 152, y, 104, 24, renderX, renderY, width, 16);
            graphics.drawString(font(), label, renderX + 3, renderY + 4, 0xffffffff, true);
        }
    }

}
