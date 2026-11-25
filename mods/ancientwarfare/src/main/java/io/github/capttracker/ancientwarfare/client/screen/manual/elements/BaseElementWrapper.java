/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/manual/elements/BaseElementWrapper.
 */
package io.github.capttracker.ancientwarfare.client.screen.manual.elements;

import io.github.capttracker.ancientwarfare.client.gui.elements.GuiElement;
import io.github.capttracker.ancientwarfare.client.screen.manual.GuiManual;
import io.github.capttracker.ancientwarfare.manual.IContentElement;
import net.minecraft.client.gui.GuiGraphics;

/** A manual content element laid out on a page: knows its position, its height, and how to draw itself. */
public abstract class BaseElementWrapper<T extends IContentElement> extends GuiElement {
    private final GuiManual gui;
    private final T element;

    public BaseElementWrapper(GuiManual gui, int topLeftX, int topLeftY, int width, int height, T element) {
        super(topLeftX, topLeftY, width, height);
        this.gui = gui;
        this.element = element;
    }

    protected T getElement() {
        return element;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

    }

    public int getTopLeftY() {
        return topLeftY;
    }

    public int getHeight() {
        return height;
    }

    /** Headings stay on the same page as the element after them. */
    public boolean shouldKeepWithNext() {
        return false;
    }

    protected GuiManual getGui() {
        return gui;
    }
}
