/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/manual/elements/ItemElementWrapper.
 */
package io.github.capttracker.ancientwarfare.client.screen.manual.elements;

import io.github.capttracker.ancientwarfare.client.screen.manual.GuiManual;
import io.github.capttracker.ancientwarfare.client.screen.manual.IElementWrapperCreator;
import io.github.capttracker.ancientwarfare.manual.ItemElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** A row of up to nine items. Hovering one shows its tooltip (new; the original only drew them). */
public class ItemElementWrapper extends BaseElementWrapper<ItemElement> {
    private static final int HEIGHT = 18;
    private static final int WIDTH = 18;
    private static final int PADDING_BETWEEN = 2;
    private static final int BOTTOM_PADDING = 2;

    public ItemElementWrapper(GuiManual gui, int topLeftY, int width, int height, ItemElement element) {
        super(gui, 0, topLeftY, width, height, element);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        ItemStack[] stacks = getElement().getItemStacks();
        for (int i = 0; i < Math.min(stacks.length, 9); i++) {
            int x = renderX + i * (WIDTH + PADDING_BETWEEN);
            graphics.renderItem(stacks[i], x, renderY);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= renderY && mouseY < renderY + 16) {
                getGui().handleItemStackTooltipRender(stacks[i], mouseX, mouseY);
            }
        }
    }

    public static class Creator implements IElementWrapperCreator<ItemElement> {
        @Override
        public List<BaseElementWrapper<?>> construct(GuiManual gui, int topLeftY, int width, int remainingPageHeight, int emptyPageHeight, ItemElement element) {
            return List.of(new ItemElementWrapper(gui, remainingPageHeight < HEIGHT ? 0 : topLeftY, width, HEIGHT + BOTTOM_PADDING, element));
        }
    }
}
