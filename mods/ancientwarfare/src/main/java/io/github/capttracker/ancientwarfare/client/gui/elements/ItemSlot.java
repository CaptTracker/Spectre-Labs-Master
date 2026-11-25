/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/ItemSlot.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase.ActivationEvent;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.ITooltipRenderer;
import io.github.capttracker.ancientwarfare.client.gui.Listener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * A client-only "slot" that shows an ItemStack. Not a real menu slot: it is used for ghost and filter
 * slots and for lists of items. Clicking it calls {@link #onSlotClicked} with the stack on the cursor.
 */
public class ItemSlot extends GuiElement {
    /** Slot highlight: white at about 55% alpha (0.55 in the original). */
    static final int HIGHLIGHT_COLOR = 0x8CFFFFFF;

    private ItemStack item = ItemStack.EMPTY;
    protected ITooltipRenderer render;
    protected boolean highlightOnMouseOver = true;
    protected boolean renderItemQuantity = true;
    protected boolean renderSlotBackground = true;
    protected boolean renderLabel = false;

    public ItemSlot(int topLeftX, int topLeftY, ItemStack item, ITooltipRenderer render) {
        super(topLeftX - 1, topLeftY - 1, 18, 18);
        this.item = item;
        this.render = render;

        Listener listener = new Listener(Listener.MOUSE_DOWN) {
            @Override
            public boolean onEvent(GuiElement widget, ActivationEvent evt) {
                if (widget.isMouseOverElement(evt.mx, evt.my)) {
                    LocalPlayer player = Minecraft.getInstance().player;
                    // the stack on the mouse cursor (1.12.2: player.inventory.getItemStack())
                    ItemStack stack = player == null ? ItemStack.EMPTY : player.containerMenu.getCarried();
                    onSlotClicked(stack, evt.mButton == 1);
                }
                return true;
            }
        };
        addNewListener(listener);
    }

    public void setRenderLabel(boolean val) {
        this.renderLabel = val;
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }

    public ItemSlot setRenderTooltip(boolean val) {
        this.renderTooltip = val;
        return this;
    }

    public ItemSlot setRenderItemQuantity(boolean val) {
        this.renderItemQuantity = val;
        return this;
    }

    public ItemSlot setHighlightOnMouseOver(boolean val) {
        this.highlightOnMouseOver = val;
        return this;
    }

    public ItemSlot setRenderSlotBackground(boolean val) {
        this.renderSlotBackground = val;
        return this;
    }

    public ItemStack getStack() {
        return item;
    }

    /** The font to draw this stack's count and label with (items may supply their own). */
    static Font fontFor(ItemStack stack) {
        Font font = IClientItemExtensions.of(stack).getFont(stack, IClientItemExtensions.FontContext.ITEM_COUNT);
        return font == null ? GuiRender.font() : font;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (visible) {
            if (renderSlotBackground) {
                GuiRender.quartered(graphics, widgetTexture1, 256, 256, 152, 120, 18, 18, renderX, renderY, width, height);
            }

            if (!this.item.isEmpty()) {
                Font font = fontFor(item);
                graphics.renderItem(item, renderX + 1, renderY + 1);
                // durability bar and cooldown overlay; the count is drawn below with the original's own layout
                graphics.renderItemDecorations(font, item, renderX + 1, renderY + 1, "");
                if (renderItemQuantity && item.getCount() > 1) {
                    renderStackSize(graphics, renderX + 1, renderY + 1, item.getCount(), font);
                }
                if (renderLabel) {
                    int x = renderX + 18;
                    int y = renderY + 3;
                    graphics.drawString(font, item.getHoverName(), x, y, 0xffffffff, true);
                }
            }

            if (isMouseOverElement(mouseX, mouseY)) {
                if (highlightOnMouseOver) {
                    // drawn with the overlay render type so it shows above the item model
                    graphics.fill(RenderType.guiOverlay(), renderX + 1, renderY + 1, renderX + width - 1, renderY + height - 1, HIGHLIGHT_COLOR);
                }
                if (renderTooltip && this.render != null) {
                    if (this.tooltip != null) {
                        this.render.handleElementTooltipRender(tooltip, mouseX, mouseY);
                    } else if (!this.item.isEmpty()) {
                        this.render.handleItemStackTooltipRender(item, mouseX, mouseY);
                    }
                }
            }
        }
    }

    /** Draws the count at the bottom right of the slot; counts over 99 are drawn at half size. */
    public void renderStackSize(GuiGraphics graphics, int renderX, int renderY, int stackSize, Font fr) {
        graphics.pose().pushPose();
        float ox = renderX + 16, oy = renderY + 8;
        // z 200 puts the text above the item model (items are drawn at z 150)
        graphics.pose().translate(ox + 0.5f, oy + 0.5f, 200.0f);

        String s1 = String.valueOf(stackSize);

        int w = fr.width(s1);
        float scale = stackSize > 99 ? 0.5f : 1.f;
        int oy1 = stackSize > 99 ? 6 : 0;

        graphics.pose().scale(scale, scale, 1.0f);

        graphics.drawString(fr, s1, -w, oy1, 0xFFFFFF, true);

        graphics.pose().popPose();
    }

    public void onSlotClicked(ItemStack stack, boolean rightClicked) {

    }

}
