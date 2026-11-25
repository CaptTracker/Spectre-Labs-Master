/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/manual/elements/TextElementWrapper.
 */
package io.github.capttracker.ancientwarfare.client.screen.manual.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.screen.manual.GuiManual;
import io.github.capttracker.ancientwarfare.client.screen.manual.IElementWrapperCreator;
import io.github.capttracker.ancientwarfare.manual.TextElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * A paragraph (or a piece of one, when it was split over pages) drawn black in the unicode font. The wrapped lines are
 * kept, so a split paragraph does not have to be measured again.
 */
public class TextElementWrapper extends BaseElementWrapper<TextElement> {
    private static final int SPACING = 9;
    private static final int TEXT_COLOR = 0xFF000000;

    private final List<FormattedCharSequence> lines;

    public TextElementWrapper(GuiManual gui, int topLeftY, int width, int height, TextElement element, List<FormattedCharSequence> lines) {
        super(gui, 0, topLeftY, width, height, element);
        this.lines = lines;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int lineHeight = GuiRender.unicodeLineHeight();
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font(), lines.get(i), renderX, renderY + i * lineHeight, TEXT_COLOR, false);
        }
    }

    @Override
    public int getHeight() {
        return super.getHeight() + SPACING;
    }

    public static class Creator implements IElementWrapperCreator<TextElement> {
        @Override
        public List<BaseElementWrapper<?>> construct(GuiManual gui, int topLeftY, int width, int remainingPageHeight, int emptyPageHeight, TextElement element) {
            List<FormattedCharSequence> textLines = font().split(GuiRender.unicode(element.getText()), width);

            int fontHeight = GuiRender.unicodeLineHeight();

            List<BaseElementWrapper<?>> ret = new ArrayList<>();
            int textHeight = fontHeight * textLines.size();
            if (textHeight <= remainingPageHeight) {
                ret.add(new TextElementWrapper(gui, topLeftY, width, textHeight, element, textLines));
                return ret;
            }
            int linesCurrent = Math.max(remainingPageHeight / fontHeight, 0);
            // Fix of the original: it skipped linesCurrent lines even when it placed none of them (room for exactly one
            // line left), which dropped that line from the manual. Now only the lines that were placed are skipped.
            int skipLines = 0;
            if (remainingPageHeight >= 2 * fontHeight) {
                ret.add(new TextElementWrapper(gui, topLeftY, width, linesCurrent * fontHeight, element, textLines.subList(0, linesCurrent)));
                skipLines = linesCurrent;
            }
            int maxLinesOnPage = Math.max(emptyPageHeight / fontHeight, 1);
            while (textLines.size() - skipLines > 0) {
                int linesToAdd = Math.min(textLines.size() - skipLines, maxLinesOnPage);
                ret.add(new TextElementWrapper(gui, 0, width, linesToAdd * fontHeight, element, textLines.subList(skipLines, skipLines + linesToAdd)));
                skipLines += linesToAdd;
            }
            return ret;
        }
    }
}
