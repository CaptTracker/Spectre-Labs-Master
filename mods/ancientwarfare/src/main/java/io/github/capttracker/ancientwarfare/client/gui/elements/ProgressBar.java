/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/ProgressBar.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import net.minecraft.client.gui.GuiGraphics;

public class ProgressBar extends GuiElement {

    private float progress;
    boolean red;

    public ProgressBar(int topLeftX, int topLeftY, int width, int height) {
        super(topLeftX, topLeftY, width, height);
    }

    public void setRed(boolean red) {
        this.red = red;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (visible) {
            GuiRender.quartered(graphics, widgetTexture2, 256, 256, 0, 0, 256, 40, renderX, renderY, width, height);

            int width = this.width - 6;
            int y = 234;
            if (red) {
                y += 10;
            }
            width = (int) ((float) width * progress);
            //152, 234
            GuiRender.quartered(graphics, widgetTexture1, 256, 256, 152, y, 104, 10, renderX + 3, renderY + 3, width, height - 6);
        }
    }

    public void setProgress(float percent) {
        this.progress = percent;
    }

}
