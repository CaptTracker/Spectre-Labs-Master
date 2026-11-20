/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/CompositeItemSlots.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.ITooltipRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrolled grid of {@link ItemSlot}s. The slots are drawn in batched passes (all backgrounds, then
 * all items, then all overlays, then the highlight) like the original, so a long list stays cheap.
 */
public class CompositeItemSlots extends CompositeScrolled {
    private final List<ItemSlot> itemSlots = new ArrayList<>();
    ITooltipRenderer render;

    public CompositeItemSlots(GuiContainerBase<?> gui, int topLeftX, int topLeftY, int width, int height, ITooltipRenderer render) {
        super(gui, topLeftX, topLeftY, width, height);
        this.render = render;
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
            if (element.renderY > renderY + height || element.renderY + element.height < renderY
                    || element.renderX > renderX + width || element.renderX + element.width < renderX) {
                continue;
            }

            if (element instanceof ItemSlot) {
                itemSlots.add((ItemSlot) element);
            } else {
                element.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        for (ItemSlot slot : itemSlots) {
            renderSlotBackground(graphics, slot);
        }
        for (ItemSlot slot : itemSlots) {
            renderItemStack(graphics, slot);
        }
        for (ItemSlot slot : itemSlots) {
            renderOverlay(graphics, slot);
        }
        for (ItemSlot slot : itemSlots) {
            renderSlotHighlight(graphics, slot, mouseX, mouseY);
        }
        //reset renderables and render-viewport
        itemSlots.clear();
        popViewport(graphics);

        //scroll-bar rendering
        scrollbar.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSlotBackground(GuiGraphics graphics, ItemSlot slot) {
        if (slot.visible && slot.renderSlotBackground) {
            GuiRender.quartered(graphics, widgetTexture1, 256, 256, 152, 120, 18, 18, slot.renderX, slot.renderY, slot.width, slot.height);
        }
    }

    private void renderSlotHighlight(GuiGraphics graphics, ItemSlot slot, int mouseX, int mouseY) {
        ItemStack stack = slot.getStack();
        if (stack.isEmpty() || !slot.visible) {
            return;
        }
        if (slot.highlightOnMouseOver && slot.isMouseOverElement(mouseX, mouseY)) {
            graphics.fill(RenderType.guiOverlay(), slot.renderX, slot.renderY, slot.renderX + slot.width, slot.renderY + slot.height, 0x80FFFFFF);
            if (slot.renderTooltip && render != null) {
                if (slot.tooltip != null) {
                    this.render.handleElementTooltipRender(slot.tooltip, mouseX, mouseY);
                } else {
                    this.render.handleItemStackTooltipRender(slot.getStack(), mouseX, mouseY);
                }
            }
        }
    }

    private void renderOverlay(GuiGraphics graphics, ItemSlot slot) {
        ItemStack stack = slot.getStack();
        if (stack.isEmpty() || !slot.visible) {
            return;
        }
        Font font = ItemSlot.fontFor(stack);
        graphics.renderItemDecorations(font, stack, slot.renderX + 1, slot.renderY + 1, "");
        if (slot.renderItemQuantity && stack.getCount() > 1) {
            slot.renderStackSize(graphics, slot.renderX + 1, slot.renderY + 1, stack.getCount(), font);
        }
    }

    private void renderItemStack(GuiGraphics graphics, ItemSlot slot) {
        ItemStack stack = slot.getStack();
        if (stack.isEmpty() || !slot.visible) {
            return;
        }
        graphics.renderItem(stack, slot.renderX + 1, slot.renderY + 1);
    }

}
