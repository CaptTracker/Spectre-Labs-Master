/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/CompositeScrolled.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase.ActivationEvent;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.IScrollableCallback;
import io.github.capttracker.ancientwarfare.client.gui.ITooltipRenderer;
import io.github.capttracker.ancientwarfare.client.gui.Listener;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;

/** A composite whose children scroll vertically under a {@link Scrollbar}. */
public class CompositeScrolled extends Composite implements IScrollableCallback {

    protected Scrollbar scrollbar;
    private int currentTop = 0;

    public CompositeScrolled(GuiContainerBase<?> gui, int topLeftX, int topLeftY, int width, int height) {
        super(gui, topLeftX, topLeftY, width, height);
        scrollbar = new Scrollbar(width - 12, 0, 12, height, this);
    }

    @Override
    public void onScrolled(int newTop) {
        currentTop = newTop;
        this.updateElementPositions();
    }

    @Override
    protected void updateElementPositions() {
        scrollbar.updateGuiPosition(renderX, renderY);
        for (GuiElement element : this.elements) {
            element.updateGuiPosition(renderX, renderY - currentTop);
        }
    }

    public void setAreaSize(int height) {
        this.scrollbar.setAreaSize(height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isMouseOverElement(mouseX, mouseY)) {
            mouseX = Integer.MIN_VALUE;
            mouseY = Integer.MIN_VALUE;
        }
        //render background before setting viewport so that it is not cropped
        GuiRender.quartered(graphics, backgroundTextureLocation, 256, 256, 0, 0, 256, 240, renderX, renderY, width, height);
        setViewport(graphics);
        for (GuiElement element : this.elements) {
            if (element.renderY > renderY + height || element.renderY + element.height < renderY) {
                continue;//manual frustrum culling of elements, on Y axis
            }
            if (element.renderX > renderX + width || element.renderX + element.width < renderX) {
                continue;//manual frustrum culling of elements, on X axis
            }
            element.render(graphics, mouseX, mouseY, partialTick);
        }
        popViewport(graphics);
        //render scrollbar after resetting viewport so that it is not cropped
        scrollbar.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void postRender(int mouseX, int mouseY, float partialTick, long tick, ITooltipRenderer rend) {
        // Only while the mouse is inside the area: children scrolled out of view must not show tooltips (local fix).
        if (!isMouseOverElement(mouseX, mouseY)) {
            mouseX = Integer.MIN_VALUE;
            mouseY = Integer.MIN_VALUE;
        }
        for (GuiElement element : this.elements) {
            element.postRender(mouseX, mouseY, partialTick, tick, rend);
        }
    }

    @Override
    protected void addDefaultListeners() {
        this.addNewListener(new Listener(Listener.ALL_EVENTS) {
            @Override
            public boolean onEvent(GuiElement widget, ActivationEvent evt) {
                if ((evt.type & Listener.KEY_TYPES) != 0) {
                    for (GuiElement element : new ArrayList<>(elements)) {
                        element.handleKeyboardInput(evt);
                    }
                } else if ((evt.type & Listener.MOUSE_TYPES) != 0) {
                    if (isMouseOverElement(evt.mx, evt.my)) {
                        boolean overElement = false;
                        scrollbar.handleMouseInput(evt);//in case input was directly over scrollbar
                        for (GuiElement element : new ArrayList<>(elements)) {
                            if (element.isMouseOverElement(evt.mx, evt.my)) {
                                overElement = evt.mw == 0 || element.scrollInput;
                            }
                            element.handleMouseInput(evt);
                        }
                        if (!overElement && !scrollbar.isMouseOverElement(evt.mx, evt.my) && evt.mw != 0) {
                            scrollbar.handleMouseScroll(evt);
                        }
                    } else {
                        if (evt.type == Listener.MOUSE_UP) {
                            for (GuiElement element : new ArrayList<>(elements)) {
                                element.handleMouseInput(evt);
                                element.setSelected(false);
                            }
                        }
                        //handle mouse leaving window, cancel scrollbar interaction
                        scrollbar.dragging = false;
                        scrollbar.pressed = false;
                    }
                }
                return true;
            }
        });
    }

    @Override
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
        int h = scrollbar.totalAreaSize;
        this.elements.remove(scrollbar);
        scrollbar = new Scrollbar(width - 12, 0, 12, height, this);
        scrollbar.setAreaSize(h);
    }

}
