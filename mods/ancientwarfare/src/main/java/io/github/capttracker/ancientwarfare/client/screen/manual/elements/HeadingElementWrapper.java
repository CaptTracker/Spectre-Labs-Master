/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/manual/elements/HeadingElementWrapper.
 */
package io.github.capttracker.ancientwarfare.client.screen.manual.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.screen.manual.GuiManual;
import io.github.capttracker.ancientwarfare.client.screen.manual.IElementWrapperCreator;
import io.github.capttracker.ancientwarfare.manual.HeadingElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** A heading: bold for level 1, underlined for the others. Always stays with the element after it. */
public class HeadingElementWrapper extends BaseElementWrapper<HeadingElement> {
    private static final int TOP_PADDING = 3;
    private static final int BOTTOM_PADDING = 3;
    private static final int TEXT_COLOR = 0xFF000000;

    private final List<FormattedCharSequence> lines;

    public HeadingElementWrapper(GuiManual gui, int topLeftY, int width, int height, HeadingElement element, List<FormattedCharSequence> lines) {
        super(gui, 0, topLeftY, width, height, element);
        this.lines = lines;
    }

    private static Component styled(HeadingElement element) {
        return GuiRender.unicode(element.getText()).copy().withStyle(element.getLevel() == 1 ? ChatFormatting.BOLD : ChatFormatting.UNDERLINE);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int lineHeight = GuiRender.unicodeLineHeight();
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font(), lines.get(i), renderX, renderY + TOP_PADDING + i * lineHeight, TEXT_COLOR, false);
        }
    }

    /** As in the original the padding is added again here although the constructor's height already holds it. */
    @Override
    public int getHeight() {
        return super.getHeight() + TOP_PADDING + BOTTOM_PADDING;
    }

    @Override
    public boolean shouldKeepWithNext() {
        return true;
    }

    public static class Creator implements IElementWrapperCreator<HeadingElement> {
        @Override
        public List<BaseElementWrapper<?>> construct(GuiManual gui, int topLeftY, int width, int remainingPageHeight, int emptyPageHeight, HeadingElement element) {
            List<FormattedCharSequence> lines = font().split(styled(element), width);
            int textHeight = GuiRender.unicodeLineHeight() * lines.size();
            return List.of(new HeadingElementWrapper(gui, topLeftY, width, textHeight + TOP_PADDING + BOTTOM_PADDING, element, lines));
        }
    }
}
