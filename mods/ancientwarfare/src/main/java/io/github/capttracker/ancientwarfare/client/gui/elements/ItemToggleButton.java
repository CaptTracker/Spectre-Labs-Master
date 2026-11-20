/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/ItemToggleButton.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.Listener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** 20x20 button showing an item; it toggles on and off (or on only, when {@code canClickUntoggle} is false). */
public abstract class ItemToggleButton extends GuiElement {
    private final ItemStack item;
    private boolean pressed = false;
    private boolean toggled = false;

    public ItemToggleButton(int topLeftX, int topLeftY, ItemStack item) {
        this(topLeftX, topLeftY, item, true);
    }

    public ItemToggleButton(int topLeftX, int topLeftY, ItemStack item, boolean canClickUntoggle) {
        super(topLeftX, topLeftY, 20, 20);
        this.item = item;

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
                if (enabled && visible && isMouseOverElement(evt.mx, evt.my) && (canClickUntoggle || !toggled)) {
                    pressed = true;
                    toggled = !toggled;
                }
                return true;
            }
        });

    }

    protected abstract void onPressed(int mButton);

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (visible) {
            int textureSize = 256;
            int startX = 0;
            int startY = 40;

            if (enabled) {
                if (isMouseOverElement(mouseX, mouseY)) {
                    startY = toggled ? 160 : 80;
                } else {
                    startY = toggled ? 120 : 40;
                }
            }
            int usedWidth = 256;
            int usedHeight = 40;
            GuiRender.quartered(graphics, widgetTexture2, textureSize, textureSize, startX, startY, usedWidth, usedHeight, renderX, renderY, width, height);

            graphics.renderItem(item, renderX + 2, renderY + 2);
        }
    }

    public boolean isToggled() {
        return toggled;
    }

    public void setToggled(boolean toggled) {
        this.toggled = toggled;
    }
}
