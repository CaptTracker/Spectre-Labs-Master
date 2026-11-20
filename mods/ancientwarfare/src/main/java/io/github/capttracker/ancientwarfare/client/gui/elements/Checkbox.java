/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/Checkbox.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase.ActivationEvent;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.Listener;
import net.minecraft.client.gui.GuiGraphics;

/*
 * Simple checkbox w/ label;<br><br>
 * <p/>
 * Inner classes can override the onToggled() method instead of adding a mouse-event listener.<br>
 * <p/>
 * Or, you may add listeners.<br>
 * Either onToggled() or listeners will be called _after_ the internal state
 * has been toggled, so when querying toggled/checked state, you will get the NEW state.<br><br>
 * <p/>
 * 12x12 is the minimum supported size.<br>
 * 16x16 is the preferred size.<br>
 * 40x40 is the maximum supported size due to textures/rendering.  Functionality will
 * work at larger sizes, but it will not render properly
 *
 * @author Shadowmage
 */
public class Checkbox extends GuiElement {

    private boolean pressed = false;
    private boolean checked = false;
    protected String label;

    /*
     * @param label (optional -- use null for none)
     */
    public Checkbox(int topLeftX, int topLeftY, int width, int height, String label) {
        super(topLeftX, topLeftY, width, height);
        this.label = label == null ? null : translate(label);
        this.setTooltipIfFound(label);
        this.addNewListener(new Listener(Listener.MOUSE_UP) {
            @Override
            public boolean onEvent(GuiElement widget, ActivationEvent evt) {
                if (pressed && visible && enabled && isMouseOverElement(evt.mx, evt.my)) {
                    GuiRender.playClickSound();
                    checked = !checked;
                    onToggled();
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (visible) {
            int y = enabled ? isMouseOverElement(mouseX, mouseY) ? 200 : 160 : 120;
            int x = checked ? 40 : 0;
            GuiRender.quartered(graphics, widgetTexture1, 256, 256, x, y, 40, 40, renderX, renderY, width, height);

            if (label != null) {
                int v = (height - 8) / 2;
                graphics.drawString(font(), label, renderX + width + 2, renderY + v, 0xffffffff, true);
            }
        }
    }

    /*
     * Anonymous classes can override this for an easy-access toggled-listener for this element
     */
    public void onToggled() {

    }

    public void setChecked(boolean val) {
        this.checked = val;
    }

    public boolean checked() {
        return checked;
    }

}
